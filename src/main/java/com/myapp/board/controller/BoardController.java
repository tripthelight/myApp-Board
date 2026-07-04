package com.myapp.board.controller;

import com.myapp.board.dto.BoardRequest;
import com.myapp.board.service.BoardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping("/list")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(boardService.findAll());
    }

    @PostMapping({"", "/", "/write", "/save"})
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, Authentication authentication) {
        BoardRequest request = new BoardRequest(
                getString(body, "title", "제목 없음"),
                getString(body, "content", ""),
                authenticatedUsername(authentication)
        );

        Object created = boardService.create(request, authenticatedUsername(authentication));

        return ResponseEntity
                .created(URI.create("/board/list"))
                .body(created);
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
    public ResponseEntity<?> updateByPut(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        return updateBoard(id, body, authentication);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateByPatch(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        return updateBoard(id, body, authentication);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateByPutPath(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        return updateBoard(id, body, authentication);
    }

    @PatchMapping("/update/{id}")
    public ResponseEntity<?> updateByPatchPath(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        return updateBoard(id, body, authentication);
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateByPost(@RequestBody Map<String, Object> body, Authentication authentication) {
        Long id = parseId(body.get("id"));
        return updateBoard(id, body, authentication);
    }

    @PostMapping("/modify")
    public ResponseEntity<?> modifyByPost(@RequestBody Map<String, Object> body, Authentication authentication) {
        Long id = parseId(body.get("id"));
        return updateBoard(id, body, authentication);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication authentication) {
        return deleteBoard(id, authentication);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteByPath(@PathVariable Long id, Authentication authentication) {
        return deleteBoard(id, authentication);
    }

    @PostMapping("/delete/{id}")
    public ResponseEntity<?> deleteByPostPath(@PathVariable Long id, Authentication authentication) {
        return deleteBoard(id, authentication);
    }

    @PostMapping("/delete")
    public ResponseEntity<?> deleteByPost(@RequestBody Map<String, Object> body, Authentication authentication) {
        Long id = parseId(body.get("id"));
        return deleteBoard(id, authentication);
    }

    private ResponseEntity<?> findBoard(Long id) {
        try {
            return ResponseEntity.ok(boardService.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<?> updateBoard(Long id, Map<String, Object> body, Authentication authentication) {
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "id is required"));
        }

        try {
            BoardRequest request = toRequest(id, body, authentication);
            return ResponseEntity.ok(boardService.update(
                    id,
                    request,
                    authenticatedUsername(authentication),
                    isAdmin(authentication)
            ));
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", "FORBIDDEN"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<?> deleteBoard(Long id, Authentication authentication) {
        if (id == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "id is required"));
        }

        try {
            boardService.delete(id, authenticatedUsername(authentication), isAdmin(authentication));
            return ResponseEntity.noContent().build();
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(403).body(Map.of("error", "FORBIDDEN"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private BoardRequest toRequest(Long id, Map<String, Object> body, Authentication authentication) {
        BoardRequest current = boardService.findById(id).toRequest();

        return new BoardRequest(
                getString(body, "title", current.getTitle()),
                getString(body, "content", current.getContent()),
                authenticatedUsername(authentication)
        );
    }

    private String authenticatedUsername(Authentication authentication) {
        return authentication.getName();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
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
}
