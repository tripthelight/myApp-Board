package com.myapp.board.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class BoardController {

    private final Map<Long, BoardItem> boards = new ConcurrentHashMap<>();

    public BoardController() {
        boards.put(1L, new BoardItem(1L, "첫 번째 게시글", "Board service 응답 테스트", "testuser"));
        boards.put(2L, new BoardItem(2L, "두 번째 게시글", "Nginx /board/list proxy 테스트", "testuser"));
    }

    @GetMapping("/list")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(
                boards.values().stream()
                        .sorted(Comparator.comparing(BoardItem::id))
                        .toList()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        return findBoard(id);
    }

    @GetMapping("/read/{id}")
    public ResponseEntity<?> read(@PathVariable Long id) {
        return findBoard(id);
    }

    @GetMapping("/detail/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        return findBoard(id);
    }

    @GetMapping(value = "/read", params = "id")
    public ResponseEntity<?> readByParam(@RequestParam Long id) {
        return findBoard(id);
    }

    @GetMapping(value = "/detail", params = "id")
    public ResponseEntity<?> detailByParam(@RequestParam Long id) {
        return findBoard(id);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateByPut(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return updateBoard(id, body);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateByPatch(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return updateBoard(id, body);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateByPutPath(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return updateBoard(id, body);
    }

    @PatchMapping("/update/{id}")
    public ResponseEntity<?> updateByPatchPath(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return updateBoard(id, body);
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateByPost(@RequestBody Map<String, Object> body) {
        Long id = parseId(body.get("id"));
        return updateBoard(id, body);
    }

    @PostMapping("/modify")
    public ResponseEntity<?> modifyByPost(@RequestBody Map<String, Object> body) {
        Long id = parseId(body.get("id"));
        return updateBoard(id, body);
    }

    private ResponseEntity<?> findBoard(Long id) {
        BoardItem board = boards.get(id);

        if (board == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(board);
    }

    private ResponseEntity<?> updateBoard(Long id, Map<String, Object> body) {
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "id is required"));
        }

        BoardItem current = boards.get(id);

        if (current == null) {
            return ResponseEntity.notFound().build();
        }

        String title = getString(body, "title", current.title());
        String content = getString(body, "content", current.content());
        String writer = getString(body, "writer", current.writer());

        BoardItem updated = new BoardItem(id, title, content, writer);
        boards.put(id, updated);

        return ResponseEntity.ok(updated);
    }

    private String getString(Map<String, Object> body, String key, String defaultValue) {
        Object value = body.get(key);

        if (value == null) {
            return defaultValue;
        }

        String stringValue = value.toString().trim();

        if (stringValue.isEmpty()) {
            return defaultValue;
        }

        return stringValue;
    }

    private Long parseId(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record BoardItem(
            Long id,
            String title,
            String content,
            String writer
    ) {
    }
}