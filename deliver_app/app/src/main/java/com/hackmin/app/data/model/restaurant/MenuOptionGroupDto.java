package com.hackmin.app.data.model.restaurant;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * 백엔드 MenuOptionGroupSerializer 대응.
 * 하나의 옵션 그룹(예: "맵기", "사이즈")과 그 안의 선택지 목록.
 */
public class MenuOptionGroupDto {

    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("is_required")
    private boolean required;

    @SerializedName("max_select")
    private int maxSelect;

    @SerializedName("options")
    private List<MenuOptionDto> options;

    public long getId() { return id; }
    public String getName() { return name; }
    public boolean isRequired() { return required; }
    public int getMaxSelect() { return maxSelect; }
    public List<MenuOptionDto> getOptions() { return options; }
}
