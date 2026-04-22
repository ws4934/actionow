package com.actionow.canvas.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.actionow.common.data.entity.TenantBaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * 画布视图实体
 * 存储预设视图和自定义视图的配置
 * 用于过滤展示画布中的节点
 *
 * @author Actionow
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "t_canvas_view", autoResultMap = true)
public class CanvasView extends TenantBaseEntity {

    /**
     * 所属画布ID
     */
    @TableField("canvas_id")
    private String canvasId;

    /**
     * 视图标识键: SCRIPT, EPISODE, STORYBOARD, CHARACTER, SCENE, PROP, ASSET
     */
    @TableField("view_key")
    private String viewKey;

    /**
     * 视图名称
     */
    private String name;

    /**
     * 视图图标
     */
    private String icon;

    /**
     * 视图类型: PRESET(预设), CUSTOM(自定义)
     */
    @TableField("view_type")
    private String viewType;

    /**
     * 根节点实体类型
     */
    @TableField("root_entity_type")
    private String rootEntityType;

    /**
     * 可见实体类型数组
     */
    @TableField(value = "visible_entity_types", typeHandler = StringArrayTypeHandler.class)
    private List<String> visibleEntityTypes;

    /**
     * 可见层级数组
     */
    @TableField(value = "visible_layers", typeHandler = StringArrayTypeHandler.class)
    private List<String> visibleLayers;

    /**
     * 过滤配置 (JSON)
     * 格式: { "focusEntityId": "xxx", "showHidden": false }
     */
    @TableField(value = "filter_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> filterConfig;

    /**
     * 视图视口配置 (JSON)
     * 格式: { "x": 0, "y": 0, "zoom": 1 }
     */
    @TableField(value = "viewport", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> viewport;

    /**
     * 布局策略: GRID, TREE, FORCE
     */
    @TableField("layout_strategy")
    private String layoutStrategy;

    /**
     * 排序序号
     */
    private Integer sequence;

    /**
     * 是否为默认视图
     */
    @TableField("is_default")
    private Boolean isDefault;

    /**
     * PostgreSQL TEXT[] 类型处理器
     */
    public static class StringArrayTypeHandler extends org.apache.ibatis.type.BaseTypeHandler<List<String>> {

        @Override
        public void setNonNullParameter(java.sql.PreparedStatement ps, int i, List<String> parameter, org.apache.ibatis.type.JdbcType jdbcType) throws java.sql.SQLException {
            // 转换为 PostgreSQL 数组
            String[] array = parameter.toArray(new String[0]);
            java.sql.Array sqlArray = ps.getConnection().createArrayOf("text", array);
            ps.setArray(i, sqlArray);
        }

        @Override
        public List<String> getNullableResult(java.sql.ResultSet rs, String columnName) throws java.sql.SQLException {
            return extractArray(rs.getArray(columnName));
        }

        @Override
        public List<String> getNullableResult(java.sql.ResultSet rs, int columnIndex) throws java.sql.SQLException {
            return extractArray(rs.getArray(columnIndex));
        }

        @Override
        public List<String> getNullableResult(java.sql.CallableStatement cs, int columnIndex) throws java.sql.SQLException {
            return extractArray(cs.getArray(columnIndex));
        }

        private List<String> extractArray(java.sql.Array array) throws java.sql.SQLException {
            if (array == null) {
                return null;
            }
            Object[] values = (Object[]) array.getArray();
            if (values == null) {
                return null;
            }
            List<String> result = new java.util.ArrayList<>();
            for (Object value : values) {
                if (value != null) {
                    result.add(value.toString());
                }
            }
            return result;
        }
    }
}
