package com.example.coconote.api.workspace.entity;

import com.example.coconote.api.section.entity.Section;
import com.example.coconote.api.workspace.dto.request.WorkspaceUpdateReqDto;
import com.example.coconote.api.workspace.dto.response.WorkspaceListResDto;
import com.example.coconote.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.Id;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workspace extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String info;

    private String logoImagePath;

    @OneToMany(mappedBy = "workspace", cascade = CascadeType.PERSIST)
    @Builder.Default
    private List<Section> sections = new ArrayList<>();

    public WorkspaceListResDto fromEntity() {
        return WorkspaceListResDto.builder()
                .name(this.name)
                .info(this.info)
                .build();
    }

    public void updateEntity(WorkspaceUpdateReqDto dto) {
        this.name = dto.getName();
        this.info = dto.getInfo();
    }

}
