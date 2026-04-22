package com.actionow.canvas.service.impl;

import com.actionow.canvas.constant.CanvasConstants;
import com.actionow.canvas.dto.edge.CanvasEdgeResponse;
import com.actionow.canvas.dto.edge.CreateEdgeRequest;
import com.actionow.canvas.dto.edge.UpdateEdgeRequest;
import com.actionow.canvas.entity.Canvas;
import com.actionow.canvas.entity.CanvasEdge;
import com.actionow.canvas.mapper.CanvasEdgeMapper;
import com.actionow.canvas.mapper.CanvasMapper;
import com.actionow.common.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CanvasEdgeServiceImpl 单元测试
 *
 * @author Actionow
 */
@ExtendWith(MockitoExtension.class)
class CanvasEdgeServiceImplTest {

    @Mock
    private CanvasEdgeMapper edgeMapper;

    @Mock
    private CanvasMapper canvasMapper;

    @InjectMocks
    private CanvasEdgeServiceImpl canvasEdgeService;

    private static final String WORKSPACE_ID = "workspace-123";
    private static final String USER_ID = "user-123";
    private static final String CANVAS_ID = "canvas-123";
    private static final String EDGE_ID = "edge-123";

    @Nested
    @DisplayName("创建边测试")
    class CreateEdgeTests {

        private Canvas mockCanvas;
        private CreateEdgeRequest request;

        @BeforeEach
        void setUp() {
            mockCanvas = new Canvas();
            mockCanvas.setId(CANVAS_ID);
            mockCanvas.setScriptId("script-123");

            request = new CreateEdgeRequest();
            request.setCanvasId(CANVAS_ID);
            request.setSourceType(CanvasConstants.EntityType.SCRIPT);
            request.setSourceId("script-123");
            request.setTargetType(CanvasConstants.EntityType.CHARACTER);
            request.setTargetId("character-123");
        }

        @Test
        @DisplayName("成功创建边 - 自动推断关系类型")
        void createEdge_success_inferRelationType() {
            // Given
            when(canvasMapper.selectById(CANVAS_ID)).thenReturn(mockCanvas);
            when(edgeMapper.selectBySourceTargetAndType(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString())).thenReturn(null);
            when(edgeMapper.selectMaxSequence(CANVAS_ID)).thenReturn(5);
            when(edgeMapper.insert(any(CanvasEdge.class))).thenReturn(1);

            // When
            CanvasEdgeResponse response = canvasEdgeService.createEdge(request, WORKSPACE_ID, USER_ID);

            // Then
            assertNotNull(response);
            assertEquals(CANVAS_ID, response.getCanvasId());
            assertEquals(CanvasConstants.EntityType.SCRIPT, response.getSourceType());
            assertEquals(CanvasConstants.EntityType.CHARACTER, response.getTargetType());
            assertEquals(CanvasConstants.RelationType.HAS_CHARACTER, response.getRelationType());

            // Verify sequence
            ArgumentCaptor<CanvasEdge> edgeCaptor = ArgumentCaptor.forClass(CanvasEdge.class);
            verify(edgeMapper).insert(edgeCaptor.capture());
            assertEquals(6, edgeCaptor.getValue().getSequence());
        }

        @Test
        @DisplayName("成功创建边 - 自定义关系类型")
        void createEdge_success_customRelationType() {
            // Given
            request.setRelationType("custom_relation");
            request.setRelationLabel("Custom Relation");

            when(canvasMapper.selectById(CANVAS_ID)).thenReturn(mockCanvas);
            when(edgeMapper.selectBySourceTargetAndType(anyString(), anyString(), anyString(),
                    anyString(), anyString(), eq("custom_relation"))).thenReturn(null);
            when(edgeMapper.selectMaxSequence(CANVAS_ID)).thenReturn(0);
            when(edgeMapper.insert(any(CanvasEdge.class))).thenReturn(1);

            // When
            CanvasEdgeResponse response = canvasEdgeService.createEdge(request, WORKSPACE_ID, USER_ID);

            // Then
            assertEquals("custom_relation", response.getRelationType());
            assertEquals("Custom Relation", response.getRelationLabel());
        }

        @Test
        @DisplayName("画布不存在 - 抛出异常")
        void createEdge_canvasNotFound_throwsException() {
            // Given
            when(canvasMapper.selectById(CANVAS_ID)).thenReturn(null);

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    canvasEdgeService.createEdge(request, WORKSPACE_ID, USER_ID));

            assertTrue(exception.getMessage().contains("画布不存在"));
        }

        @Test
        @DisplayName("边类型不允许 - 抛出异常")
        void createEdge_edgeNotAllowed_throwsException() {
            // Given
            // CHARACTER -> SCRIPT is not allowed in SCRIPT dimension
            request.setSourceType(CanvasConstants.EntityType.CHARACTER);
            request.setSourceId("character-123");
            request.setTargetType(CanvasConstants.EntityType.SCRIPT);
            request.setTargetId("script-123");

            when(canvasMapper.selectById(CANVAS_ID)).thenReturn(mockCanvas);

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    canvasEdgeService.createEdge(request, WORKSPACE_ID, USER_ID));

            assertTrue(exception.getMessage().contains("不允许从"));
        }

        @Test
        @DisplayName("边已存在 - 抛出异常")
        void createEdge_edgeAlreadyExists_throwsException() {
            // Given
            CanvasEdge existingEdge = new CanvasEdge();
            existingEdge.setId(EDGE_ID);

            when(canvasMapper.selectById(CANVAS_ID)).thenReturn(mockCanvas);
            when(edgeMapper.selectBySourceTargetAndType(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString())).thenReturn(existingEdge);

            // When & Then
            BusinessException exception = assertThrows(BusinessException.class, () ->
                    canvasEdgeService.createEdge(request, WORKSPACE_ID, USER_ID));

            assertTrue(exception.getMessage().contains("边已存在"));
        }

        @Test
        @DisplayName("设置默认线条样式")
        void createEdge_defaultLineStyle() {
            // Given
            when(canvasMapper.selectById(CANVAS_ID)).thenReturn(mockCanvas);
            when(edgeMapper.selectBySourceTargetAndType(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString())).thenReturn(null);
            when(edgeMapper.selectMaxSequence(CANVAS_ID)).thenReturn(0);
            when(edgeMapper.insert(any(CanvasEdge.class))).thenReturn(1);

            // When
            CanvasEdgeResponse response = canvasEdgeService.createEdge(request, WORKSPACE_ID, USER_ID);

            // Then
            assertNotNull(response.getLineStyle());
            assertEquals(CanvasConstants.DefaultLineStyle.STROKE_COLOR,
                    response.getLineStyle().get("strokeColor"));
            assertEquals(CanvasConstants.DefaultLineStyle.STROKE_WIDTH,
                    response.getLineStyle().get("strokeWidth"));
        }

        @Test
        @DisplayName("设置自定义线条样式")
        void createEdge_customLineStyle() {
            // Given
            Map<String, Object> customStyle = new HashMap<>();
            customStyle.put("strokeColor", "#ff0000");
            customStyle.put("strokeWidth", 3);
            request.setLineStyle(customStyle);

            when(canvasMapper.selectById(CANVAS_ID)).thenReturn(mockCanvas);
            when(edgeMapper.selectBySourceTargetAndType(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString())).thenReturn(null);
            when(edgeMapper.selectMaxSequence(CANVAS_ID)).thenReturn(0);
            when(edgeMapper.insert(any(CanvasEdge.class))).thenReturn(1);

            // When
            CanvasEdgeResponse response = canvasEdgeService.createEdge(request, WORKSPACE_ID, USER_ID);

            // Then
            assertEquals("#ff0000", response.getLineStyle().get("strokeColor"));
            assertEquals(3, response.getLineStyle().get("strokeWidth"));
        }
    }

    @Nested
    @DisplayName("更新边测试")
    class UpdateEdgeTests {

        private CanvasEdge existingEdge;
        private UpdateEdgeRequest request;

        @BeforeEach
        void setUp() {
            existingEdge = new CanvasEdge();
            existingEdge.setId(EDGE_ID);
            existingEdge.setCanvasId(CANVAS_ID);
            existingEdge.setRelationLabel("Original Label");
            existingEdge.setPathType(CanvasConstants.PathType.BEZIER);
            existingEdge.setSequence(1);
            existingEdge.setLineStyle(new HashMap<>());

            request = new UpdateEdgeRequest();
        }

        @Test
        @DisplayName("更新边标签 - 成功")
        void updateEdge_updateLabel_success() {
            // Given
            request.setRelationLabel("Updated Label");

            when(edgeMapper.selectById(EDGE_ID)).thenReturn(existingEdge);
            when(edgeMapper.updateById(any(CanvasEdge.class))).thenReturn(1);

            // When
            CanvasEdgeResponse response = canvasEdgeService.updateEdge(EDGE_ID, request, USER_ID);

            // Then
            assertEquals("Updated Label", response.getRelationLabel());
        }

        @Test
        @DisplayName("更新路径类型 - 成功")
        void updateEdge_updatePathType_success() {
            // Given
            request.setPathType(CanvasConstants.PathType.STEP);

            when(edgeMapper.selectById(EDGE_ID)).thenReturn(existingEdge);
            when(edgeMapper.updateById(any(CanvasEdge.class))).thenReturn(1);

            // When
            CanvasEdgeResponse response = canvasEdgeService.updateEdge(EDGE_ID, request, USER_ID);

            // Then
            assertEquals(CanvasConstants.PathType.STEP, response.getPathType());
        }

        @Test
        @DisplayName("更新序号 - 成功")
        void updateEdge_updateSequence_success() {
            // Given
            request.setSequence(10);

            when(edgeMapper.selectById(EDGE_ID)).thenReturn(existingEdge);
            when(edgeMapper.updateById(any(CanvasEdge.class))).thenReturn(1);

            // When
            CanvasEdgeResponse response = canvasEdgeService.updateEdge(EDGE_ID, request, USER_ID);

            // Then
            assertEquals(10, response.getSequence());
        }

        @Test
        @DisplayName("边不存在 - 抛出异常")
        void updateEdge_edgeNotFound_throwsException() {
            // Given
            when(edgeMapper.selectById(EDGE_ID)).thenReturn(null);

            // When & Then
            assertThrows(BusinessException.class, () ->
                    canvasEdgeService.updateEdge(EDGE_ID, request, USER_ID));
        }
    }

    @Nested
    @DisplayName("删除边测试")
    class DeleteEdgeTests {

        @Test
        @DisplayName("删除存在的边 - 成功")
        void deleteEdge_existingEdge_success() {
            // Given
            CanvasEdge existingEdge = new CanvasEdge();
            existingEdge.setId(EDGE_ID);

            when(edgeMapper.selectById(EDGE_ID)).thenReturn(existingEdge);
            when(edgeMapper.deleteById(EDGE_ID)).thenReturn(1);

            // When & Then
            assertDoesNotThrow(() -> canvasEdgeService.deleteEdge(EDGE_ID, USER_ID));
            verify(edgeMapper).deleteById(EDGE_ID);
        }

        @Test
        @DisplayName("删除不存在的边 - 抛出异常")
        void deleteEdge_edgeNotFound_throwsException() {
            // Given
            when(edgeMapper.selectById(EDGE_ID)).thenReturn(null);

            // When & Then
            assertThrows(BusinessException.class, () ->
                    canvasEdgeService.deleteEdge(EDGE_ID, USER_ID));
        }

        @Test
        @DisplayName("删除画布所有边")
        void deleteByCanvasId_success() {
            // Given
            when(edgeMapper.deleteByCanvasId(CANVAS_ID)).thenReturn(5);

            // When
            canvasEdgeService.deleteByCanvasId(CANVAS_ID);

            // Then
            verify(edgeMapper).deleteByCanvasId(CANVAS_ID);
        }

        @Test
        @DisplayName("删除实体相关边")
        void deleteByEntity_success() {
            // Given
            when(edgeMapper.deleteByEntity(CanvasConstants.EntityType.CHARACTER, "char-123"))
                    .thenReturn(3);

            // When
            canvasEdgeService.deleteByEntity(CanvasConstants.EntityType.CHARACTER, "char-123");

            // Then
            verify(edgeMapper).deleteByEntity(CanvasConstants.EntityType.CHARACTER, "char-123");
        }
    }

    @Nested
    @DisplayName("批量创建边测试")
    class BatchCreateEdgesTests {

        @Test
        @DisplayName("批量创建边 - 部分成功")
        void batchCreateEdges_partialSuccess() {
            // Given
            Canvas mockCanvas = new Canvas();
            mockCanvas.setId(CANVAS_ID);
            mockCanvas.setScriptId("script-123");

            CreateEdgeRequest request1 = new CreateEdgeRequest();
            request1.setCanvasId(CANVAS_ID);
            request1.setSourceType(CanvasConstants.EntityType.SCRIPT);
            request1.setSourceId("script-1");
            request1.setTargetType(CanvasConstants.EntityType.CHARACTER);
            request1.setTargetId("char-1");

            CreateEdgeRequest request2 = new CreateEdgeRequest();
            request2.setCanvasId(CANVAS_ID);
            request2.setSourceType(CanvasConstants.EntityType.CHARACTER); // Invalid: CHARACTER can't point to SCRIPT
            request2.setSourceId("char-1");
            request2.setTargetType(CanvasConstants.EntityType.SCRIPT);
            request2.setTargetId("script-1");

            when(canvasMapper.selectById(CANVAS_ID)).thenReturn(mockCanvas);
            when(edgeMapper.selectBySourceTargetAndType(anyString(), anyString(), anyString(),
                    anyString(), anyString(), anyString())).thenReturn(null);
            when(edgeMapper.selectMaxSequence(CANVAS_ID)).thenReturn(0);
            when(edgeMapper.insert(any(CanvasEdge.class))).thenReturn(1);

            // When
            List<CanvasEdgeResponse> responses = canvasEdgeService.batchCreateEdges(
                    Arrays.asList(request1, request2), WORKSPACE_ID, USER_ID);

            // Then
            assertEquals(1, responses.size()); // Only first one succeeded
            assertEquals("script-1", responses.get(0).getSourceId());
        }
    }

    @Nested
    @DisplayName("查询边测试")
    class QueryEdgeTests {

        @Test
        @DisplayName("根据 ID 查询边")
        void getEdge_success() {
            // Given
            CanvasEdge edge = new CanvasEdge();
            edge.setId(EDGE_ID);
            edge.setCanvasId(CANVAS_ID);
            edge.setSourceType(CanvasConstants.EntityType.SCRIPT);
            edge.setTargetType(CanvasConstants.EntityType.CHARACTER);

            when(edgeMapper.selectById(EDGE_ID)).thenReturn(edge);

            // When
            CanvasEdgeResponse response = canvasEdgeService.getEdge(EDGE_ID);

            // Then
            assertNotNull(response);
            assertEquals(EDGE_ID, response.getId());
        }

        @Test
        @DisplayName("根据 ID 查询边不存在 - 抛出异常")
        void getEdge_notFound_throwsException() {
            // Given
            when(edgeMapper.selectById(EDGE_ID)).thenReturn(null);

            // When & Then
            assertThrows(BusinessException.class, () ->
                    canvasEdgeService.getEdge(EDGE_ID));
        }

        @Test
        @DisplayName("根据画布 ID 查询所有边")
        void listByCanvasId_success() {
            // Given
            CanvasEdge edge1 = new CanvasEdge();
            edge1.setId("edge-1");
            edge1.setSequence(1);

            CanvasEdge edge2 = new CanvasEdge();
            edge2.setId("edge-2");
            edge2.setSequence(2);

            when(edgeMapper.selectByCanvasId(CANVAS_ID)).thenReturn(Arrays.asList(edge1, edge2));

            // When
            List<CanvasEdgeResponse> responses = canvasEdgeService.listByCanvasId(CANVAS_ID);

            // Then
            assertEquals(2, responses.size());
        }
    }

    @Nested
    @DisplayName("验证边和推断关系类型测试")
    class ValidateAndInferTests {

        @Test
        @DisplayName("验证边在统一画布允许")
        void validateEdge_unifiedCanvas_allowed() {
            // Given
            Canvas canvas = new Canvas();
            canvas.setScriptId("script-123");
            when(canvasMapper.selectById(CANVAS_ID)).thenReturn(canvas);

            // When
            boolean result = canvasEdgeService.validateEdge(CANVAS_ID,
                    CanvasConstants.EntityType.SCRIPT, "script-1",
                    CanvasConstants.EntityType.CHARACTER, "char-1");

            // Then
            assertTrue(result);
        }

        @Test
        @DisplayName("验证边在统一画布不允许")
        void validateEdge_unifiedCanvas_notAllowed() {
            // Given
            Canvas canvas = new Canvas();
            canvas.setScriptId("script-123");
            when(canvasMapper.selectById(CANVAS_ID)).thenReturn(canvas);

            // When
            boolean result = canvasEdgeService.validateEdge(CANVAS_ID,
                    CanvasConstants.EntityType.CHARACTER, "char-1",
                    CanvasConstants.EntityType.SCRIPT, "script-1");

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("画布不存在时验证返回 false")
        void validateEdge_canvasNotFound_returnsFalse() {
            // Given
            when(canvasMapper.selectById(CANVAS_ID)).thenReturn(null);

            // When
            boolean result = canvasEdgeService.validateEdge(CANVAS_ID,
                    CanvasConstants.EntityType.SCRIPT, "script-1",
                    CanvasConstants.EntityType.CHARACTER, "char-1");

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("推断 SCRIPT -> EPISODE 关系类型")
        void inferRelationType_scriptToEpisode() {
            // When
            String relationType = canvasEdgeService.inferRelationType(
                    CanvasConstants.EntityType.SCRIPT, CanvasConstants.EntityType.EPISODE);

            // Then
            assertEquals(CanvasConstants.RelationType.HAS_EPISODE, relationType);
        }

        @Test
        @DisplayName("推断 SCRIPT -> CHARACTER 关系类型")
        void inferRelationType_scriptToCharacter() {
            // When
            String relationType = canvasEdgeService.inferRelationType(
                    CanvasConstants.EntityType.SCRIPT, CanvasConstants.EntityType.CHARACTER);

            // Then
            assertEquals(CanvasConstants.RelationType.HAS_CHARACTER, relationType);
        }

        @Test
        @DisplayName("推断 STORYBOARD -> CHARACTER 关系类型")
        void inferRelationType_storyboardToCharacter() {
            // When
            String relationType = canvasEdgeService.inferRelationType(
                    CanvasConstants.EntityType.STORYBOARD, CanvasConstants.EntityType.CHARACTER);

            // Then
            assertEquals(CanvasConstants.RelationType.APPEARS_IN, relationType);
        }

        @Test
        @DisplayName("推断 STORYBOARD -> SCENE 关系类型")
        void inferRelationType_storyboardToScene() {
            // When
            String relationType = canvasEdgeService.inferRelationType(
                    CanvasConstants.EntityType.STORYBOARD, CanvasConstants.EntityType.SCENE);

            // Then
            assertEquals(CanvasConstants.RelationType.TAKES_PLACE_IN, relationType);
        }

        @Test
        @DisplayName("推断任意 -> ASSET 关系类型")
        void inferRelationType_anyToAsset() {
            // When
            String relationType = canvasEdgeService.inferRelationType(
                    CanvasConstants.EntityType.CHARACTER, CanvasConstants.EntityType.ASSET);

            // Then
            assertEquals(CanvasConstants.RelationType.HAS_ASSET, relationType);
        }
    }
}
