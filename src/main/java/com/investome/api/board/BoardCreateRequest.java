package com.investome.api.board;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BoardCreateRequest {
    private String category;
    private String title;
    private String content;
    private String imageData;
    private String imageName;
}