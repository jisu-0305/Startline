package org.example.gogoma.domain.marathon.service;

import com.google.gson.Gson;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.gogoma.common.util.GoogleDocumentAiUtil;
import org.example.gogoma.common.util.GoogleVisionApiUtil;
import org.example.gogoma.common.util.S3ImageUtil;
import org.example.gogoma.controller.request.CreateMarathonRequest;
import org.example.gogoma.controller.request.MarathonDetailRequest;
import org.example.gogoma.controller.request.MarathonSearchRequest;
import org.example.gogoma.controller.response.MarathonDetailResponse;
import org.example.gogoma.controller.response.MarathonSearchResponse;
import org.example.gogoma.domain.marathon.dto.CustomMultipartFile;
import org.example.gogoma.domain.marathon.dto.MarathonDataDto;
import org.example.gogoma.domain.marathon.dto.MarathonPreviewDto;
import org.example.gogoma.domain.marathon.entity.Marathon;
import org.example.gogoma.domain.marathon.entity.MarathonType;
import org.example.gogoma.domain.marathon.enums.MarathonStatus;
import org.example.gogoma.domain.marathon.repository.MarathonCustomRepository;
import org.example.gogoma.domain.marathon.repository.MarathonCustomRepositoryImpl;
import org.example.gogoma.domain.marathon.repository.MarathonRepository;
import org.example.gogoma.domain.marathon.repository.MarathonTypeRepository;
import org.example.gogoma.exception.ExceptionCode;
import org.example.gogoma.exception.type.BusinessException;
import org.example.gogoma.exception.type.DbException;
import org.example.gogoma.external.kakao.local.KakaoAddressResponse;
import org.example.gogoma.external.kakao.local.KakaoLocalClient;
import org.example.gogoma.external.kakao.local.KakaoRegionResponse;
import org.example.gogoma.external.openai.ChatGptClient;
import org.example.gogoma.external.openai.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarathonServiceImpl implements MarathonService {

    private final S3ImageUtil s3ImageUtil;
    private final ChatGptClient chatGptClient;
    private final GoogleDocumentAiUtil googleDocumentAiUtil;
    private final GoogleVisionApiUtil googleVisionApiUtil;
    private final MarathonRepository marathonRepository;
    private final MarathonTypeRepository marathonTypeRepository;
    private final MarathonCustomRepository marathonCustomRepository;
    private final KakaoLocalClient kakaoLocalClient;
    private static final Gson gson = new Gson();

    @Override
    @Transactional
    public void createMarathon(CreateMarathonRequest createMarathonRequest,
                               MultipartFile thumbnailFile,
                               MultipartFile infoImageFile,
                               MultipartFile courseImageFile) {

        try {
            log.info("Processing marathon creation...");

            MultipartFile multipartFile;
            String thumbnailImageUrl = "";
            String courseImageUrl = "";
            String infoImageUrl = "";
            String information = "";
            String prompt = Prompt.DEFAULT.getPrompt();

            // 썸네일 이미지 저장
            if (createMarathonRequest.getThumbnailUrl() != null && !createMarathonRequest.getThumbnailUrl().isBlank()) {
                multipartFile = convertUrlToMultipartFile(createMarathonRequest.getThumbnailUrl());
                thumbnailImageUrl = s3ImageUtil.uploadImage(multipartFile, "marathon/thumbnail");
            } else if (thumbnailFile.getSize() > 0) {
                thumbnailImageUrl = s3ImageUtil.uploadImage(thumbnailFile, "marathon/thumbnail");
            }

            // 코스 이미지 저장
            if (createMarathonRequest.getCourseImageUrl() != null && !createMarathonRequest.getCourseImageUrl().isBlank()) {
                multipartFile = convertUrlToMultipartFile(createMarathonRequest.getCourseImageUrl());
                courseImageUrl = s3ImageUtil.uploadImage(multipartFile, "marathon/course");
            } else if (courseImageFile.getSize() > 0) {
                courseImageUrl = s3ImageUtil.uploadImage(courseImageFile, "marathon/course");
            }

            // 대회 요강 이미지 저장
            if (createMarathonRequest.getInfoImageUrl() != null && !createMarathonRequest.getInfoImageUrl().isBlank()) {
                multipartFile = convertUrlToMultipartFile(createMarathonRequest.getInfoImageUrl());
                infoImageUrl = s3ImageUtil.uploadImage(multipartFile, "marathon/info");
                prompt += getPrompt(createMarathonRequest.getInfoImageUrl());
            } else if (infoImageFile.getSize() > 0) {
                infoImageUrl = s3ImageUtil.uploadImage(infoImageFile, "marathon/info");
                prompt += getPrompt(infoImageFile);
            }

            prompt += createMarathonRequest.getTextInfo();

            log.info(prompt + "\n");

            information = chatGptClient.chat(prompt);

            MarathonDataDto data = gson.fromJson(information, MarathonDataDto.class);

            log.info(gson.toJson(data) + "\n");

            LocalDateTime registrationStartDateTime = parseTime(data.getMarathonInfo().getRegistrationStartDateTime());
            LocalDateTime registrationEndDateTime = parseTime(data.getMarathonInfo().getRegistrationEndDateTime());
            LocalDateTime raceStartTime = parseTime(data.getMarathonInfo().getRaceStartTime());

            KakaoAddressResponse kakaoAddressResponse = kakaoLocalClient.getCoordinates(data.getMarathonInfo().getLocation());

            KakaoRegionResponse kakaoRegionResponse;
            String city = "";
            String region = "";
            String district = "";

            if (!kakaoAddressResponse.getDocuments().isEmpty()) {
                kakaoRegionResponse = kakaoLocalClient.getRegionFromCoordinates(
                        Double.valueOf(kakaoAddressResponse.getDocuments().get(0).getX()),
                        Double.valueOf(kakaoAddressResponse.getDocuments().get(0).getY()));

                city = kakaoRegionResponse.getDocuments().get(0).getRegion1DepthName();
                region = kakaoRegionResponse.getDocuments().get(0).getRegion2DepthName();
                district = kakaoRegionResponse.getDocuments().get(0).getRegion3DepthName();
            }

            Marathon marathon = Marathon.builder()
                    .title(data.getMarathonInfo().getTitle())
                    .registrationStartDateTime(registrationStartDateTime)
                    .registrationEndDateTime(registrationEndDateTime)
                    .raceStartTime(raceStartTime)
                    .accountBank(data.getMarathonInfo().getAccountBank())
                    .accountNumber(data.getMarathonInfo().getAccountNumber())
                    .accountName(data.getMarathonInfo().getAccountName())
                    .location(data.getMarathonInfo().getLocation())
                    .hostList(data.getMarathonInfo().getHostList())
                    .organizerList(data.getMarathonInfo().getOrganizerList())
                    .sponsorList(data.getMarathonInfo().getSponsorList())
                    .qualifications(data.getMarathonInfo().getQualifications())
                    .viewCount(0)
                    .year(raceStartTime != null ? String.valueOf(raceStartTime.getYear()) : null)
                    .month(raceStartTime != null ? String.valueOf(raceStartTime.getMonthValue()) : null)
                    .location(data.getMarathonInfo().getLocation())
                    .city(city)
                    .region(region)
                    .district(district)
                    .marathonStatus(MarathonStatus.OPEN)
                    .thumbnailImage(thumbnailImageUrl)
                    .courseImage(courseImageUrl)
                    .infoImage(infoImageUrl)
                    .build();

            marathonRepository.save(marathon);

            List<MarathonType> marathonTypeList = data.getMarathonInfo().getMarathonTypeDtoList().stream()
                    .map(dto -> MarathonType.of(dto, marathon.getId()))
                    .toList();

            marathonTypeRepository.saveAll(marathonTypeList);

        } catch (IOException e) {
            throw new BusinessException(ExceptionCode.BUSINESS_ERROR);
        }
    }

    private MultipartFile convertUrlToMultipartFile(String imageUrl) throws IOException {
        byte[] imageBytes = downloadImageBytes(imageUrl);
        return new CustomMultipartFile(imageBytes, imageUrl);
    }

    private byte[] downloadImageBytes(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);

        try (InputStream inputStream = url.openStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            BufferedImage urlImage = ImageIO.read(inputStream);
            ImageIO.write(urlImage, "jpg", bos);
            return bos.toByteArray();
        }
    }

    private String getPrompt(String imageUrl){
        return googleVisionApiUtil.extractTextFromImageUrl(imageUrl) + "\n" +
                googleDocumentAiUtil.extractTextFromImageUrl(imageUrl);
    }

    private String getPrompt(MultipartFile imageFile) {
        return googleVisionApiUtil.extractTextFromImage(imageFile) + "\n" +
                googleDocumentAiUtil.extractTextFromImage(imageFile);
    }

    /**
     * 문자열을 LocalDateTime으로 변환
     */
    public static LocalDateTime parseTime(String dateTime) {
        DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        return Optional.ofNullable(dateTime)
                .filter(date -> !date.isBlank())
                .map(dt -> LocalDateTime.parse(dt, FORMATTER))
                .orElse(null);
    }

    @Override
    public MarathonDetailResponse getMarathonById(int id) {
        Marathon marathon = marathonRepository.findById(id)
                .orElseThrow(() -> new DbException(ExceptionCode.MARATHON_NOT_FOUND));

        List<MarathonType> marathonTypeList = marathonTypeRepository.findAllByMarathonId(id);

        return MarathonDetailResponse.of(marathon, marathonTypeList);
    }

    @Override
    @Transactional
    public void updateMarathon(MarathonDetailRequest marathonDetailRequest) {
        Marathon marathon = marathonRepository.findById(marathonDetailRequest.getMarathon().getId())
                .orElseThrow(() -> new DbException(ExceptionCode.MARATHON_NOT_FOUND));

        marathon.update(
                marathonDetailRequest.getMarathon().getTitle(),
                marathonDetailRequest.getMarathon().getRegistrationStartDateTime(),
                marathonDetailRequest.getMarathon().getRegistrationEndDateTime(),
                marathonDetailRequest.getMarathon().getRaceStartTime(),
                marathonDetailRequest.getMarathon().getAccountBank(),
                marathonDetailRequest.getMarathon().getAccountNumber(),
                marathonDetailRequest.getMarathon().getAccountName(),
                marathonDetailRequest.getMarathon().getLocation(),
                marathonDetailRequest.getMarathon().getQualifications(),
                marathonDetailRequest.getMarathon().getYear(),
                marathonDetailRequest.getMarathon().getMonth(),
                marathonDetailRequest.getMarathon().getCity(),
                marathonDetailRequest.getMarathon().getRegion(),
                marathonDetailRequest.getMarathon().getDistrict(),
                marathonDetailRequest.getMarathon().getFormType(),
                marathonDetailRequest.getMarathon().getFormUrl(),
                marathonDetailRequest.getMarathon().getHostList(),
                marathonDetailRequest.getMarathon().getOrganizerList(),
                marathonDetailRequest.getMarathon().getSponsorList(),
                marathonDetailRequest.getMarathon().getMarathonStatus()
        );

        marathonTypeRepository.deleteAllByMarathonId(marathon.getId());

        List<MarathonType> marathonTypeList = marathonDetailRequest.getMarathonTypeList().stream()
                .map(type -> MarathonType.builder()
                        .marathonId(marathon.getId())
                        .courseType(type.getCourseType())
                        .price(type.getPrice())
                        .etc(type.getEtc())
                        .build())
                .toList();


        marathonTypeRepository.saveAll(marathonTypeList);
    }

    @Override
    @Transactional
    public void deleteMarathon(int id) {
        Marathon marathon = marathonRepository.findById(id)
                .orElseThrow(() -> new DbException(ExceptionCode.MARATHON_NOT_FOUND));

        if (marathon.getThumbnailImage() != null && !marathon.getThumbnailImage().isBlank()) {
            s3ImageUtil.deleteImageByUrl(marathon.getThumbnailImage());
        }

        if (marathon.getInfoImage() != null && !marathon.getInfoImage().isBlank()) {
            s3ImageUtil.deleteImageByUrl(marathon.getInfoImage());
        }

        if (marathon.getCourseImage() != null && !marathon.getCourseImage().isBlank()) {
            s3ImageUtil.deleteImageByUrl(marathon.getCourseImage());
        }

        marathonRepository.deleteById(marathon.getId());

        marathonTypeRepository.deleteAllByMarathonId(marathon.getId());
    }

    @Override
    public MarathonSearchResponse searchMarathonList(MarathonSearchRequest marathonSearchRequest) {
        List<Marathon> marathonList = marathonCustomRepository.searchMarathonByConitions(
                marathonSearchRequest.getMarathonStatus(),
                marathonSearchRequest.getCity(),
                marathonSearchRequest.getYear(),
                marathonSearchRequest.getMonth(),
                marathonSearchRequest.getKeyword(),
                marathonSearchRequest.getCourseTypeList()
        );

        List<MarathonPreviewDto> marathonPreviewDtoList = marathonList.stream()
                .map(marathon -> {
                    List<MarathonType> marathonTypeList = marathonTypeRepository.findAllByMarathonId(marathon.getId());

                    List<String> courseTypeList = marathonTypeRepository.findAllByMarathonId(marathon.getId())
                            .stream()
                            .map(MarathonType::getCourseType)
                            .collect(Collectors.toSet())
                            .stream()
                            .sorted()
                            .toList();

                    return MarathonPreviewDto.builder()
                            .id(marathon.getId())
                            .title(marathon.getTitle())
                            .registrationStartDateTime(marathon.getRegistrationStartDateTime())
                            .registrationEndDateTime(marathon.getRegistrationEndDateTime())
                            .raceStartTime(marathon.getRaceStartTime())
                            .courseTypeList(courseTypeList)
                            .location(marathon.getLocation())
                            .city(marathon.getCity())
                            .region(marathon.getRegion())
                            .district(marathon.getDistrict())
                            .marathonStatus(marathon.getMarathonStatus())
                            .thumbnailImage(marathon.getThumbnailImage())
                            .dDay(calculateDDay(marathon.getRaceStartTime()))
                            .marathonTypeList(marathonTypeList)
                            .build();
                })
                .sorted(Comparator.comparing(MarathonPreviewDto::getRaceStartTime))
                .toList();

        return MarathonSearchResponse.of(marathonPreviewDtoList);
    }

    private String calculateDDay(LocalDateTime raceStartTime) {
        if (raceStartTime == null) {
            return "D-?";
        }
        long daysBetween = ChronoUnit.DAYS.between(LocalDate.now(), raceStartTime.toLocalDate());
        return daysBetween >= 0 ? "D-" + daysBetween : "D+" + Math.abs(daysBetween);
    }
}
