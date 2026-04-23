package com.investome.api.assetcommunity;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetCommentCreateRequest {

    private String assetName;
    private String content;
}
