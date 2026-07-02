package com.myapp.board.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class BoardController {

    @GetMapping("/list")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(List.of(
                Map.of(
                        "id", 1,
                        "title", "첫 번째 게시글",
                        "content", "Board service 응답 테스트",
                        "writer", "testuser"
                ),
                Map.of(
                        "id", 2,
                        "title", "두 번째 게시글",
                        "content", "Nginx /board/list proxy 테스트",
                        "writer", "testuser"
                )
        ));
    }
}