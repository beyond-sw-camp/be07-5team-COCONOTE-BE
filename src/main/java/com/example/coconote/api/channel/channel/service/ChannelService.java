package com.example.coconote.api.channel.channel.service;

import com.example.coconote.api.channel.channel.dto.request.ChannelAccessReqDto;
import com.example.coconote.api.channel.channel.dto.request.ChannelCreateReqDto;
import com.example.coconote.api.channel.channel.dto.request.ChannelUpdateReqDto;
import com.example.coconote.api.channel.channel.dto.response.ChannelDetailResDto;
import com.example.coconote.api.channel.channel.entity.Channel;
import com.example.coconote.api.channel.channel.entity.ChannelType;
import com.example.coconote.api.channel.channel.repository.ChannelRepository;
import com.example.coconote.api.channel.channelMember.entity.ChannelMember;
import com.example.coconote.api.channel.channelMember.entity.ChannelRole;
import com.example.coconote.api.channel.channelMember.repository.ChannelMemberRepository;
import com.example.coconote.api.drive.dto.response.FolderAllListResDto;
import com.example.coconote.api.drive.entity.Folder;
import com.example.coconote.api.drive.repository.FolderRepository;
import com.example.coconote.api.member.entity.Member;
import com.example.coconote.api.member.repository.MemberRepository;
import com.example.coconote.api.search.dto.EntityType;
import com.example.coconote.api.search.dto.IndexEntityMessage;
import com.example.coconote.api.search.entity.ChannelDocument;
import com.example.coconote.api.search.entity.WorkspaceMemberDocument;
import com.example.coconote.api.search.mapper.ChannelMapper;
import com.example.coconote.api.search.service.SearchService;
import com.example.coconote.api.section.entity.Section;
import com.example.coconote.api.section.entity.SectionType;
import com.example.coconote.api.section.repository.SectionRepository;
import com.example.coconote.api.workspace.workspace.entity.Workspace;
import com.example.coconote.api.workspace.workspace.repository.WorkspaceRepository;
import com.example.coconote.api.workspace.workspaceMember.entity.WorkspaceMember;
import com.example.coconote.api.workspace.workspaceMember.repository.WorkspaceMemberRepository;
import com.example.coconote.common.IsDeleted;
import com.example.coconote.global.fileUpload.repository.FileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.example.coconote.api.drive.service.FolderService.getFolderAllListResDto;

@Service
@Transactional
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final SectionRepository sectionRepository;
    private final WorkspaceRepository workspaceRepository;
    private final FolderRepository folderRepository;
    private final MemberRepository memberRepository;
    private final FileRepository fileRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final SearchService searchService;
    private final ChannelMapper channelMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Transactional
    public ChannelDetailResDto channelCreate(ChannelCreateReqDto dto, String email) {
        Section section = getSectionBySectionId(dto.getSectionId());
        if (section.getIsDeleted().equals(IsDeleted.Y)) {
            throw new IllegalArgumentException("이미 삭제된 섹션입니다.");
        }
//        1. 해당 섹션에 채널 생성
        Channel channel = dto.toEntity(section);
        Member member = getMemberByEmail(email);
        WorkspaceMember workspaceMember = workspaceMemberRepository.findByMemberAndWorkspaceAndIsDeleted(member, section.getWorkspace(), IsDeleted.N).orElseThrow(() -> new EntityNotFoundException("워크스페이스 회원이 존재하지 않습니다."));

        ChannelMember channelMember = ChannelMember.builder()
                .workspaceMember(workspaceMember)
                .channel(channel)
                .channelRole(ChannelRole.MANAGER)
                .build();

        channelMemberRepository.save(channelMember);
        channel.getChannelMembers().add(channelMember);
        channelRepository.save(channel);

        ChannelDocument document = channelMapper.toDocument(channel);
        IndexEntityMessage<ChannelDocument> indexEntityMessage = new IndexEntityMessage<>(channel.getSection().getWorkspace().getWorkspaceId(), EntityType.CHANNEL, document);
        kafkaTemplate.send("channel_entity_search", indexEntityMessage.toJson());

        createDefaultFolder(channel);
        ChannelDetailResDto resDto = channel.fromEntity(section);
        return resDto;
    }

    public void createDefaultFolder(Channel channel) {
        Folder rootFolder = Folder.builder()
                .folderName("root")
                .channel(channel)
                .build();
        Folder folder = Folder.builder()
                .folderName("캔버스 자동업로드 폴더")
                .channel(channel)
                .parentFolder(rootFolder)
                .build();
        Folder folder2 = Folder.builder()
                .folderName("쓰레드 자동업로드 폴더")
                .channel(channel)
                .parentFolder(rootFolder)
                .build();
        folderRepository.save(rootFolder);
        folderRepository.save(folder);
        folderRepository.save(folder2);
    }

    public List<ChannelDetailResDto> channelList(Long sectionId, String email) {
        // 1. 이메일을 통해 멤버 조회
        Member member = getMemberByEmail(email);

        // 2. 섹션 ID를 통해 섹션 조회
        Section section = getSectionBySectionId(sectionId);

        // 3. 섹션이 삭제되었는지 확인
        if (section.getIsDeleted().equals(IsDeleted.Y)) {
            throw new IllegalArgumentException("이미 삭제된 섹션입니다.");
        }

        // 4. 해당 멤버가 섹션의 워크스페이스에 속해 있는지 확인
        WorkspaceMember workspaceMember = workspaceMemberRepository.findByMemberAndWorkspaceAndIsDeleted(member, section.getWorkspace(), IsDeleted.N)
                .orElseThrow(() -> new EntityNotFoundException("워크스페이스 회원이 존재하지 않습니다."));

        // 5. 쿼리를 통해 채널 조회 (멤버와 워크스페이스를 사용)
        List<Channel> channels = channelRepository.findChannelsByWorkspaceMemberOrPublic(section, IsDeleted.N, workspaceMember);

        // 6. 채널을 DTO로 변환하여 반환
        return channels.stream()
                .map(channel -> channel.fromEntity(section))  // 각 채널을 DTO로 변환
                .collect(Collectors.toList());
//      채널리스트
//        1. 해당 섹션에 존재하는 채널리스트
//        2. 멤버에따라 공개 비공개 여부가 달라짐
//        3. public 채널은 모두 보여줌
//        4. Public false 면 해당 멤버가 채널멤버인지 확인후 채널멤버면 보여줌


//        List<Channel> channels = channelRepository.findBySectionAndIsDeleted(section, IsDeleted.N);
//        List<ChannelDetailResDto> dtos = new ArrayList<>();
//        for(Channel c : channels) {
//            // 비공개채널이고 내가 채널멤버도 아니면 -> continue
//            // 내가 채널멤버인지 아닌지 알아보기 -> email과 channel 정보로
//            // email로 멤버 정보를 받아온다
//            // channelMembers 탐색 >
//            List<ChannelMember> cMembers = c.getChannelMembers();
//            for(ChannelMember cm : cMembers) {
//                if(c.getIsPublic()  || cm.getWorkspaceMember().getMember().equals(member)) { // 비공개채널이고 내가 채널멤버도 아님 -> continue
//                    dtos.add(c.fromEntity(section));
//                }
//            }
//        }
//        return dtos;
    }


    @Transactional
    public ChannelDetailResDto channelUpdate(Long id, ChannelUpdateReqDto dto, String email) {
        Channel channel = channelRepository.findById(id).orElseThrow(()->new EntityNotFoundException("존재하지 않는 채널입니다."));
        if(!checkChannelAuthorization(id, email)) {
            throw new IllegalArgumentException("채널을 수정할 권한이 없습니다.");
        }
        if (channel.getIsDeleted().equals(IsDeleted.Y)) {
            throw new IllegalArgumentException("이미 삭제된 채널입니다.");
        }
        channel.updateEntity(dto);
        channelRepository.save(channel);

        ChannelDocument document = channelMapper.toDocument(channel);
        IndexEntityMessage<ChannelDocument> indexEntityMessage = new IndexEntityMessage<>(channel.getSection().getWorkspace().getWorkspaceId(),EntityType.CHANNEL , document);
        kafkaTemplate.send("channel_entity_search", indexEntityMessage.toJson());

        return channel.fromEntity(channel.getSection());
    }


    @Transactional
    public void channelDelete(Long id, String email) {
        Channel channel = channelRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 채널입니다."));
        if (!checkChannelAuthorization(id, email)) {
            throw new IllegalArgumentException("채널을 삭제할 권한이 없습니다.");
        }
        if (channel.getIsDeleted().equals(IsDeleted.Y)) {
            throw new IllegalArgumentException("이미 삭제된 채널입니다.");
        }
        if(channel.getChannelType().equals(ChannelType.DEFAULT)) {
            throw new IllegalArgumentException("기본 채널은 삭제할 수 없습니다.");
        }
        channel.deleteEntity();
        searchService.deleteChannel(channel.getSection().getWorkspace().getWorkspaceId(), channel.getChannelId());
    }

    public FolderAllListResDto channelDrive(Long channelId, String email) {
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다."));
        Channel channel = channelRepository.findById(channelId).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 채널입니다."));
        if (channel.getIsDeleted().equals(IsDeleted.Y)) {
            throw new IllegalArgumentException("이미 삭제된 채널입니다.");
        }
//        루트 폴더 찾기
        Folder rootFolder = folderRepository.findByChannelAndParentFolderIsNull(channel).orElseThrow(() -> new EntityNotFoundException("찾을 수 없습니다."));

        return getFolderAllListResDto(rootFolder, folderRepository, fileRepository);
    }

    public List<ChannelDetailResDto> bookmarkList(Long workspaceId, String email) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 워크스페이스입니다."));
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다."));
        WorkspaceMember workspaceMember = workspaceMemberRepository.findByMemberAndWorkspaceAndIsDeleted(member, workspace, IsDeleted.N).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 회원입니다."));

        if (workspace.getIsDeleted().equals(IsDeleted.Y)) {
            throw new IllegalArgumentException("이미 삭제된 워크스페이스입니다.");
        }
        List<ChannelDetailResDto> bookmarkChannels = new ArrayList<>();
        if(workspaceMember.getChannelMembers() != null) {
            for(ChannelMember cm : workspaceMember.getChannelMembers()) {
                if(cm.getIsBookmark()) {
                    bookmarkChannels.add(cm.getChannel().fromEntity(cm.getChannel().getSection()));
                }
            }
        }
        return bookmarkChannels;
    }


    private Boolean checkChannelAuthorization(Long channelId, String email) {
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다."));
        Channel channel = channelRepository.findById(channelId).orElseThrow(() -> new EntityNotFoundException("채널을 찾을 수 없습니다."));
        Section section = sectionRepository.findById(channel.getSection().getSectionId()).orElseThrow(() -> new EntityNotFoundException("섹션을 찾을 수 없습니다."));
        Workspace workspace = workspaceRepository.findById(section.getWorkspace().getWorkspaceId()).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 워크스페이스입니다."));
        WorkspaceMember workspaceMember = workspaceMemberRepository.findByMemberAndWorkspaceAndIsDeleted(member, workspace, IsDeleted.N).orElseThrow(() -> new EntityNotFoundException("워크스페이스 회원을 찾을 수 없습니다."));
        ChannelMember channelMember = channelMemberRepository.findByChannelAndWorkspaceMemberAndIsDeleted(channel, workspaceMember, IsDeleted.N).orElseThrow(() -> new EntityNotFoundException("채널 회원을 찾을 수 없습니다."));
        return channelMember.getChannelRole().equals(ChannelRole.MANAGER);
    }

    public ChannelDetailResDto channelFirst(Long workspaceId, String email) {
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다."));
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 워크스페이스입니다."));
        WorkspaceMember workspaceMember = workspaceMemberRepository.findByMemberAndWorkspaceAndIsDeleted(member, workspace, IsDeleted.N).orElseThrow(() -> new EntityNotFoundException("존재하지 않는 워크스페이스멤버입니다."));
        List<ChannelMember> channelMembers = channelMemberRepository.findByWorkspaceMemberAndIsDeleted(workspaceMember, IsDeleted.N);
        if (channelMembers.isEmpty()) {
            throw new IllegalArgumentException("회원을 찾을 수 없습니다.");
        }
        for(Section s : workspace.getSections()) {
            if(s.getSectionType().equals(SectionType.DEFAULT) && s.getChannels()!=null) {
                for(Channel c : s.getChannels()) {
                    if(c.getChannelType().equals(ChannelType.DEFAULT)) {
                        return c.fromEntity(s);					}
                }
            }
        }
        throw new IllegalArgumentException("기본 채널을 찾을 수 없습니다.");
    }

    public boolean channelIsJoin(Long id, String email) {
        Member member = memberRepository.findByEmail(email).orElseThrow(() -> new EntityNotFoundException("회원을 찾을 수 없습니다."));
        Channel channel = channelRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("채널을 찾을 수 없습니다."));
        Section section = sectionRepository.findById(channel.getSection().getSectionId()).orElseThrow(() -> new EntityNotFoundException("섹션을 찾을 수 없습니다."));
        Workspace workspace = workspaceRepository.findById(section.getWorkspace().getWorkspaceId()).orElseThrow(() -> new EntityNotFoundException("워크스페이스를 찾을 수 없습니다."));
        WorkspaceMember workspaceMember = workspaceMemberRepository.findByMemberAndWorkspaceAndIsDeleted(member, workspace, IsDeleted.N).orElseThrow(() -> new EntityNotFoundException("워크스페이스멤버를 찾을 수 없습니다."));

//        채널멤버가 존재하면 true, 존재하지 않으면 false
        ChannelMember channelMember = channelMemberRepository.findByChannelAndWorkspaceMemberAndIsDeleted(channel, workspaceMember, IsDeleted.N).orElse(null);
        return channelMember != null;
    }


    public ChannelDetailResDto channelDetail(Long channelId) {
        Channel channel = channelRepository.findById(channelId).orElseThrow(() -> new EntityNotFoundException("채널을 찾을 수 없습니다."));
        return channel.fromEntity(channel.getSection());
    }
    //    공통 메서드
    private Member getMemberByEmail(String email) {
        return memberRepository.findByEmail(email).orElseThrow(() -> new IllegalArgumentException("사용자가 존재하지 않습니다."));
    }

    private Section getSectionBySectionId(Long sectionId) {
        return sectionRepository.findById(sectionId).orElseThrow(() -> new IllegalArgumentException("섹션이 존재하지 않습니다."));
    }

    private Channel getChannelByChannelId(Long channelId) {
        return channelRepository.findById(channelId).orElseThrow(() -> new IllegalArgumentException("채널이 존재하지 않습니다."));
    }

    private Workspace getWorkspaceByWorkspaceId(Long workspaceId) {
        return workspaceRepository.findById(workspaceId).orElseThrow(() -> new IllegalArgumentException("워크스페이스가 존재하지 않습니다."));
    }

    public ChannelDetailResDto channelChangeAccessLevel(ChannelAccessReqDto dto, String email) {
        Channel channel = channelRepository.findById(dto.getChannelId()).orElseThrow(()->new EntityNotFoundException("존재하지 않는 채널입니다."));
        if(!checkChannelAuthorization(dto.getChannelId(), email)) {
            throw new IllegalArgumentException("채널을 수정할 권한이 없습니다.");
        }
        if (channel.getIsDeleted().equals(IsDeleted.Y)) {
            throw new IllegalArgumentException("이미 삭제된 채널입니다.");
        }
        if(channel.getChannelType().equals(ChannelType.DEFAULT)) {
            throw new IllegalArgumentException("기본 채널은 반드시 공개 채널이어야 합니다.");
        }

        channel.changeAccessLevel(dto.getIsPublic());
        channelRepository.save(channel);

        ChannelDocument document = channelMapper.toDocument(channel);
        IndexEntityMessage<ChannelDocument> indexEntityMessage = new IndexEntityMessage<>(channel.getSection().getWorkspace().getWorkspaceId(),EntityType.CHANNEL , document);
        kafkaTemplate.send("channel_entity_search", indexEntityMessage.toJson());

        return channel.fromEntity(channel.getSection());
    }
}

