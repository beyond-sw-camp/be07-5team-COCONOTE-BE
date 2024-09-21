package com.example.coconote.api.channel.service;

import com.example.coconote.api.channel.dto.request.ChannelCreateReqDto;
import com.example.coconote.api.channel.dto.request.ChannelUpdateReqDto;
import com.example.coconote.api.channel.dto.response.ChannelListResDto;
import com.example.coconote.api.channel.entity.Channel;
import com.example.coconote.api.channel.repository.ChannelRepository;
import com.example.coconote.api.drive.entity.Folder;
import com.example.coconote.api.drive.repository.FolderRepository;
import com.example.coconote.api.section.entity.Section;
import com.example.coconote.api.section.repository.SectionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final SectionRepository sectionRepository;
    private final FolderRepository folderRepository;


    @Transactional
    public ChannelListResDto channelCreate(ChannelCreateReqDto dto) {
        Section section = sectionRepository.findById(dto.getSectionId()).orElseThrow(()-> new EntityNotFoundException("존재하지 않는 섹션입니다."));

        Channel channel = dto.toEntity(section);
        channelRepository.save(channel);
        createDefaultFolder(channel);
        ChannelListResDto resDto = channel.fromEntity(section);

        return resDto;
    }

    private void createDefaultFolder(Channel channel) {
        Folder rootFolder = Folder.builder()
                .folderName("root")
                .channel(channel)
                .build();
        Folder folder = Folder.builder()
                .folderName("자동업로드 폴더")
                .channel(channel)
                .parentFolder(rootFolder)
                .build();
        folderRepository.save(rootFolder);
        folderRepository.save(folder);
    }

    public List<ChannelListResDto> channelList(Long sectionId) {

        Section section = sectionRepository.findById(sectionId).orElseThrow(()->new EntityNotFoundException("없는 섹션입니다."));
        List<Channel> channels = channelRepository.findAll();
        List<ChannelListResDto> dtos = new ArrayList<>();


        for(Channel c : channels) {
            dtos.add(c.fromEntity(section));
        }
        return dtos;
    }

    public Channel channelUpdate(Long id, ChannelUpdateReqDto dto) {
        Channel channel = channelRepository.findById(id).orElseThrow(()->new EntityNotFoundException(" 찾을 수 없습니다."));
        channel.updateEntity(dto);
        return channel;
    }

    public void channelDelete(Long id) {
        Channel channel = channelRepository.findById(id).orElseThrow(()->new EntityNotFoundException("찾을 수 없습니다."));
        channel.deleteEntity();
    }
}
