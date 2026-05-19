package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.boards.domain.TaskItem;
import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.boards.BoardsCreateTaskRequest;
import com.massimotter.weave.backend.model.boards.BoardsMoveTaskRequest;
import com.massimotter.weave.backend.model.boards.BoardsPreviewResponse;
import com.massimotter.weave.backend.service.BoardsFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Boards preview", description = "Hidden provider-neutral Boards/Tasks preview facade backed by a local in-memory adapter.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Boards preview runtime is disabled or provider capability is unavailable.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class BoardsController {

    private final BoardsFacadeService boardsFacadeService;

    public BoardsController(BoardsFacadeService boardsFacadeService) {
        this.boardsFacadeService = boardsFacadeService;
    }

    @GetMapping("/api/boards/preview")
    @Operation(summary = "Read the hidden Boards/Tasks preview facade")
    @ApiResponse(responseCode = "200", description = "Provider-neutral Boards/Tasks preview snapshot.",
            content = @Content(schema = @Schema(implementation = BoardsPreviewResponse.class)))
    public BoardsPreviewResponse preview(@AuthenticationPrincipal Jwt jwt) {
        return boardsFacadeService.preview(jwt);
    }

    @PostMapping("/api/boards/{boardId}/tasks")
    @Operation(summary = "Create a task in the hidden Boards/Tasks preview facade")
    @ApiResponse(responseCode = "200", description = "Created provider-neutral task.",
            content = @Content(schema = @Schema(implementation = TaskItem.class)))
    public TaskItem createTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String boardId,
            @Valid @RequestBody BoardsCreateTaskRequest request) {
        return boardsFacadeService.createTask(jwt, boardId, request);
    }

    @PostMapping("/api/boards/tasks/{taskId}/move")
    @Operation(summary = "Move a task without drag-and-drop in the hidden Boards/Tasks preview facade")
    @ApiResponse(responseCode = "200", description = "Moved provider-neutral task.",
            content = @Content(schema = @Schema(implementation = TaskItem.class)))
    public TaskItem moveTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String taskId,
            @Valid @RequestBody BoardsMoveTaskRequest request) {
        return boardsFacadeService.moveTask(jwt, taskId, request);
    }

    @PostMapping("/api/boards/tasks/{taskId}/complete")
    @Operation(summary = "Complete a task without drag-and-drop in the hidden Boards/Tasks preview facade")
    @ApiResponse(responseCode = "200", description = "Completed provider-neutral task.",
            content = @Content(schema = @Schema(implementation = TaskItem.class)))
    public TaskItem completeTask(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String taskId) {
        return boardsFacadeService.completeTask(jwt, taskId);
    }
}
