package com.fininsight.transaction.service;

import com.fininsight.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 库存服务 — 秒杀/领料出库
 * 层1: Redis Lua 原子扣（主力，O(1)）
 * 层2: MySQL 乐观锁 version（兜底）
 * 层3: 工单取消 → 退库存（补偿）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {
    private final RedisTemplate<String, String> redisTemplate;
    private final JdbcTemplate jdbc;

    // 原子扣库存 + 一人一单
    private static final String LUA_DEDUCT =
        "local s=tonumber(redis.call('GET',KEYS[1]) or '0') " +
        "local q=tonumber(ARGV[1]) " +
        "local uid=ARGV[2] " +
        "if redis.call('EXISTS',KEYS[2])==1 then return -1 end " +  // 已抢过
        "if s>=q then redis.call('DECRBY',KEYS[1],q); redis.call('SETEX',KEYS[2],3600,uid); return 1 " +
        "else return 0 end";

    private final DefaultRedisScript<Long> deductScript = new DefaultRedisScript<>(LUA_DEDUCT, Long.class);

    public void loadToRedis() {
        for (var r : jdbc.queryForList("SELECT part_id, stock FROM inventory")) {
            redisTemplate.opsForValue().set("inv:" + r.get("part_id"), String.valueOf(r.get("stock")));
        }
        log.info("[库存] Redis库存已加载");
    }

    public Map<String, Object> deduct(String partId, int qty, String orderId, String userId) {
        // 层1: Redis Lua 原子扣 + 一人一单
        Long ok = redisTemplate.execute(deductScript, List.of("inv:" + partId, "claimed:" + partId + ":" + userId), String.valueOf(qty), userId);
        if (ok != null && ok == -1) {
            throw new BizException(429, "您已经领取过该配件/优惠券，每人限领一次");
        }
        if (ok == null || ok == 0) {
            String s = redisTemplate.opsForValue().get("inv:" + partId);
            throw new BizException(400, "库存不足，剩余: " + (s != null ? s : "0"));
        }

        // 层2: MySQL 乐观锁
        try {
            int rows = jdbc.update(
                "UPDATE inventory SET stock=stock-?, version=version+1 WHERE part_id=? AND stock>=?", qty, partId, qty);
            if (rows == 0) {
                redisTemplate.opsForValue().increment("inv:" + partId, qty);
                throw new BizException(400, "库存扣减冲突(乐观锁)，请重试");
            }
            jdbc.update("INSERT INTO part_consumption (order_id, part_id, quantity) VALUES (?,?,?)", orderId, partId, qty);
        } catch (BizException e) { throw e; }
        catch (Exception e) {
            redisTemplate.opsForValue().increment("inv:" + partId, qty);
            throw new BizException(500, "出库失败: " + e.getMessage());
        }

        String remaining = redisTemplate.opsForValue().get("inv:" + partId);
        log.info("[出库] {} x{} → {}, 剩余: {}", partId, qty, orderId, remaining);
        return Map.of("partId", partId, "qty", qty, "remaining", remaining != null ? remaining : "0", "orderId", orderId);
    }

    // 退库原子脚本：防重复 + 库存恢复
    private static final String LUA_REFUND =
        "if redis.call('EXISTS',KEYS[1])==1 then return -1 end " +  // 已退过
        "redis.call('INCRBY',KEYS[2],tonumber(ARGV[1])); redis.call('SETEX',KEYS[1],86400,'1'); return 1";

    private final DefaultRedisScript<Long> refundScript = new DefaultRedisScript<>(LUA_REFUND, Long.class);

    /** 退库存（幂等防重复） */
    public void refund(String partId, int qty, String orderId) {
        // Lua 原子检测：refunded:订单号 存在则拒绝
        Long ok = redisTemplate.execute(refundScript,
            List.of("refunded:" + orderId, "inv:" + partId), String.valueOf(qty));
        if (ok != null && ok == -1) {
            throw new BizException(409, "该工单已退过库存，请勿重复操作");
        }
        if (ok == null || ok == 0) {
            throw new BizException(500, "退库操作失败");
        }
        // MySQL 乐观锁兜底
        int rows = jdbc.update(
            "UPDATE inventory SET stock=stock+?, version=version+1 WHERE part_id=?", qty, partId);
        if (rows == 0) throw new BizException(500, "退库失败(DB乐观锁冲突)");

        jdbc.update("DELETE FROM part_consumption WHERE order_id=? AND part_id=?", orderId, partId);
        String remaining = redisTemplate.opsForValue().get("inv:" + partId);
        log.info("[退库] {} x{} ← 工单{}, 库存恢复至: {}", partId, qty, orderId, remaining);
    }

    public List<Map<String, Object>> listAll() {
        List<Map<String, Object>> r = new ArrayList<>();
        for (var row : jdbc.queryForList("SELECT * FROM inventory ORDER BY part_id")) {
            String rs = redisTemplate.opsForValue().get("inv:" + row.get("part_id"));
            row.put("redis_stock", rs != null ? Integer.parseInt(rs) : 0);
            r.add(row);
        }
        return r;
    }
}
