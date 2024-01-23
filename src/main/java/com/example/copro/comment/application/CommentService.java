package com.example.copro.comment.application;

import com.amazonaws.services.kms.model.NotFoundException;
import com.example.copro.board.domain.Board;
import com.example.copro.board.domain.repository.BoardRepository;
import com.example.copro.board.exception.BoardNotFoundException;
import com.example.copro.comment.api.dto.request.CommentSaveReqDto;
import com.example.copro.comment.api.dto.request.CommentUpdateReqDto;
import com.example.copro.comment.api.dto.response.CommentResDto;
import com.example.copro.comment.domain.Comment;
import com.example.copro.comment.domain.repository.CommentRepository;
import com.example.copro.comment.exception.CommentNotFoundException;
import com.example.copro.member.domain.Member;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final BoardRepository boardRepository;

    @Transactional
    public CommentResDto insert(Long boardId, CommentSaveReqDto commentSaveReqDto, Member member) {
        Board board = boardRepository.findById(boardId).orElseThrow(() -> new BoardNotFoundException(boardId));

        Comment parentComment = getParentComment(commentSaveReqDto);
        Comment comment = builderComment(commentSaveReqDto, member, board, parentComment);

        Comment savedComment = commentRepository.save(comment);
        return CommentResDto.from(savedComment);
    }

    private Comment getParentComment(CommentSaveReqDto commentSaveReqDto) {
        if (commentSaveReqDto.parentId() != null) {
            return commentRepository.findById(commentSaveReqDto.parentId())
                    .orElseThrow(() -> new CommentNotFoundException(
                            commentSaveReqDto.parentId()));
        }
        return null;
    }

    private Comment builderComment(CommentSaveReqDto commentSaveReqDto, Member member, Board board,
                                   Comment parentComment) {
        return Comment.builder()
                .content(commentSaveReqDto.content())
                .writer(member)
                .isDeleted(false)
                .board(board)
                .parent(parentComment)
                .build();
    }

    @Transactional
    public CommentResDto update(Long commentId, CommentUpdateReqDto commentUpdateReqDto, Member member) {
        Comment comment = commentRepository.findById(commentId).orElseThrow(() -> new CommentNotFoundException(commentId));

        validateCommentOwner(comment, member);

        comment.updateContent(commentUpdateReqDto.content());
        return CommentResDto.from(comment);
    }

    @Transactional
    public void delete(Member member, Long commentId) {
        Comment comment = commentRepository.findCommentByCommentIdWithParent(commentId).orElseThrow(() -> new CommentNotFoundException(commentId));

        validateCommentOwner(comment, member);

        if (!comment.getChildren().isEmpty()) { // 자식이 있으면 상태만 변경
            comment.changeIsDeleted(true);
        } else { // 삭제 가능한 조상 댓글을 구해서 삭제
            commentRepository.delete(getDeletableAncestorComment(comment));
        }
    }

    private Comment getDeletableAncestorComment(Comment comment) {
        Comment parent = comment.getParent(); // 현재 댓글의 부모를 구함
        if (parent != null && parent.getChildren().size() == 1 && parent.getIsDeleted())
        // 부모가 있고, 부모의 자식이 1개(지금 삭제하는 댓글)이고, 부모의 삭제 상태가 TRUE인 댓글이라면 재귀
        {
            return getDeletableAncestorComment(parent);
        }
        return comment; // 삭제해야하는 댓글 반환
    }

    private void validateCommentOwner(Comment comment, Member member) {
        if (!comment.getWriter().getMemberId().equals(member.getMemberId())) {
            throw new NotFoundException("본인이 아니면 댓글을 조작할 수 없습니다.");
        }
    }

}