package com.fininsight.transaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fininsight.transaction.entity.WorkOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

@Mapper
public interface WorkOrderMapper extends BaseMapper<WorkOrder> {

    @Select("SELECT location, COUNT(*) as orders, SUM(profit) as total_profit, " +
            "AVG(profit_rate) as avg_rate FROM work_order " +
            "WHERE user_id = #{userId} AND order_time BETWEEN #{start} AND #{end} " +
            "GROUP BY location ORDER BY total_profit DESC")
    List<Map<String, Object>> profitByLocation(Long userId, String start, String end);

    @Select("SELECT service_type, COUNT(*) as orders, SUM(total_revenue) as revenue, " +
            "SUM(profit) as profit FROM work_order " +
            "WHERE user_id = #{userId} GROUP BY service_type")
    List<Map<String, Object>> statsByServiceType(Long userId);

    /** 按月统计利润 */
    @Select("SELECT DATE_FORMAT(order_time,'%Y-%m') as month, COUNT(*) as orders, " +
            "SUM(total_revenue) as revenue, SUM(profit) as profit, " +
            "AVG(profit_rate) as avg_rate, " +
            "SUM(CASE WHEN profit < 0 THEN 1 ELSE 0 END) as loss_orders " +
            "FROM work_order WHERE user_id = #{userId} " +
            "GROUP BY month ORDER BY month DESC")
    List<Map<String, Object>> monthlyStats(Long userId);

    /** 按年+类型统计 */
    @Select("SELECT DATE_FORMAT(order_time,'%Y') as year, service_type, COUNT(*) as orders, " +
            "SUM(total_revenue) as revenue, SUM(profit) as profit, AVG(profit_rate) as avg_rate " +
            "FROM work_order WHERE user_id = #{userId} " +
            "GROUP BY year, service_type ORDER BY year DESC, profit DESC")
    List<Map<String, Object>> yearlyByType(Long userId);

    /** 按年统计 */
    @Select("SELECT DATE_FORMAT(order_time,'%Y') as year, COUNT(*) as orders, " +
            "SUM(total_revenue) as revenue, SUM(profit) as profit, AVG(profit_rate) as avg_rate " +
            "FROM work_order WHERE user_id = #{userId} " +
            "GROUP BY year ORDER BY year DESC")
    List<Map<String, Object>> yearlyStats(Long userId);

    /** 按类型统计材料费占比 */
    @Select("SELECT service_type, COUNT(*) as orders, " +
            "SUM(material_cost) as total_material, SUM(total_revenue) as revenue, " +
            "ROUND(SUM(material_cost)/SUM(total_revenue)*100,2) as material_ratio " +
            "FROM work_order WHERE user_id = #{userId} GROUP BY service_type ORDER BY material_ratio DESC")
    List<Map<String, Object>> materialRatioByType(Long userId);

    /** 按年+类型统计材料费占比 */
    @Select("SELECT DATE_FORMAT(order_time,'%Y') as year, service_type, COUNT(*) as orders, " +
            "SUM(material_cost) as total_material, SUM(total_revenue) as revenue, " +
            "ROUND(SUM(material_cost)/SUM(total_revenue)*100,2) as material_ratio " +
            "FROM work_order WHERE user_id = #{userId} " +
            "GROUP BY year, service_type ORDER BY year DESC, material_ratio DESC")
    List<Map<String, Object>> materialRatioByYearType(Long userId);

    /** 总体概览 */
    @Select("SELECT COUNT(*) as total, SUM(total_revenue) as revenue, " +
            "SUM(profit) as profit, AVG(profit_rate) as avg_rate, " +
            "SUM(CASE WHEN profit < 0 THEN 1 ELSE 0 END) as loss_count, " +
            "SUM(CASE WHEN ai_category='高利润' THEN 1 ELSE 0 END) as high_profit_count " +
            "FROM work_order WHERE user_id = #{userId}")
    Map<String, Object> overview(Long userId);
}
