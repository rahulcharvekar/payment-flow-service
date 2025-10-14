package com.example.paymentflow.board.entity;

public class BoardReceiptProcessRequest {
    private String boardRef;
    private String utrNumber;
    private String checker;

    public String getBoardRef() {
        return boardRef;
    }

    public void setBoardRef(String boardRef) {
        this.boardRef = boardRef;
    }

    public String getUtrNumber() {
        return utrNumber;
    }

    public void setUtrNumber(String utrNumber) {
        this.utrNumber = utrNumber;
    }

    public String getChecker() {
        return checker;
    }

    public void setChecker(String checker) {
        this.checker = checker;
    }
}
