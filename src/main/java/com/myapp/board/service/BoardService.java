package com.myapp.board.service;

import com.myapp.board.domain.Board;
import com.myapp.board.dto.BoardRequest;
import com.myapp.board.dto.BoardResponse;
import com.myapp.board.repository.BoardRepository;
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
                request.getTitle(),
                request.getContent(),
                request.getWriter()
        );

        Board savedBoard = boardRepository.save(board);

        return savedBoard.toResponse();
    }

    @Transactional
    public BoardResponse update(Long boardId, BoardRequest request) {
        Board board = findBoard(boardId);

        board.update(
                request.getTitle(),
                request.getContent(),
                request.getWriter()
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
}
