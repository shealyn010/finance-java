package com.fininsight.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fininsight.transaction.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {

    /** 按地点统计工单利润 */
    @Select("SELECT location, COUNT(*) as orders, SUM(profit) as total_profit, " +
            "AVG(profit_rate) as avg_rate FROM work_order " +
            "WHERE user_id = #{userId} AND order_time BETWEEN #{start} AND #{end} " +
            "GROUP BY location ORDER BY total_profit DESC")
    List<Map<String, Object>> profitByLocation(Long userId, String start, String end);

    /** 按服务类型统计 */
    @Select("SELECT service_type, COUNT(*) as orders, SUM(total_revenue) as revenue, " +
            "SUM(profit) as profit FROM work_order " +
            "WHERE user_id = #{userId} GROUP BY service_type")
    List<Map<String, Object>> statsByServiceType(Long userId);
}
