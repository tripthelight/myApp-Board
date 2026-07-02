package com.myapp.board.service;

import com.myapp.board.domain.Board;
import com.myapp.board.dto.BoardRequest;
import com.myapp.board.dto.BoardResponse;
import com.myapp.board.repository.BoardRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;

    public BoardService(BoardRepository boardRepository) {
        this.boardRepository = boardRepository;
    }

    @PostConstruct
    @Transactional
    public void seed() {
        if (boardRepository.count() > 0) {
            return;
        }

        boardRepository.save(new Board("첫 번째 게시글", "Board service 응답 테스트", "testuser"));
        boardRepository.save(new Board("두 번째 게시글", "Nginx /board/list proxy 테스트", "testuser"));
    }

    public List<BoardResponse> findAll() {
        return boardRepository.findAllByOrderByIdDesc()
                .stream()
                .map(Board::toResponse)
                .toList();
    }

    public BoardResponse findById(Long boardId) {
        Board board = findBoard(boardId);
        return board.toResponse();
    }

    @Transactional
    public BoardResponse create(BoardRequest request) {
        Board board = new Board(
                normalize(request.getTitle(), "제목 없음"),
                normalize(request.getContent(), ""),
                normalize(request.getWriter(), "anonymous")
        );

        Board savedBoard = boardRepository.save(board);

        return savedBoard.toResponse();
    }

    @Transactional
    public BoardResponse update(Long boardId, BoardRequest request) {
        Board board = findBoard(boardId);

        board.update(
                normalize(request.getTitle(), board.toResponse().getTitle()),
                normalize(request.getContent(), board.toResponse().getContent()),
                normalize(request.getWriter(), board.toResponse().getWriter())
        );

        return board.toResponse();
    }

    @Transactional
    public void delete(Long boardId) {
        Board board = findBoard(boardId);
        boardRepository.delete(board);
    }

    private Board findBoard(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found. id=" + boardId));
    }

    private String normalize(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value.trim();
    }
}