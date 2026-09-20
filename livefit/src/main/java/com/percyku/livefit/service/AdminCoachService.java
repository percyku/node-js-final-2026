package com.percyku.livefit.service;

import com.percyku.livefit.common.ApiException;
import com.percyku.livefit.common.DateTimeUtils;
import com.percyku.livefit.common.ErrorMessages;
import com.percyku.livefit.common.ValidUtils;
import com.percyku.livefit.dto.admin.CoachCourseDetailResponse;
import com.percyku.livefit.dto.admin.CoachCourseListItem;
import com.percyku.livefit.dto.admin.CoachCourseRequest;
import com.percyku.livefit.dto.admin.CoachCourseResponse;
import com.percyku.livefit.dto.admin.CoachProfileRequest;
import com.percyku.livefit.dto.admin.CoachProfileResponse;
import com.percyku.livefit.dto.admin.CoachRevenueResponse;
import com.percyku.livefit.dto.admin.CoachSignupRequest;
import com.percyku.livefit.dto.admin.CoachSignupResponse;
import com.percyku.livefit.entity.Coach;
import com.percyku.livefit.entity.CoachWithSkill;
import com.percyku.livefit.entity.Course;
import com.percyku.livefit.entity.User;
import com.percyku.livefit.repository.CoachRepository;
import com.percyku.livefit.repository.CoachWithSkillRepository;
import com.percyku.livefit.repository.CourseBookingRepository;
import com.percyku.livefit.repository.CourseRepository;
import com.percyku.livefit.repository.CreditPackageRepository;
import com.percyku.livefit.repository.UserRepository;
import com.percyku.livefit.repository.projection.CourseParticipantCount;
import com.percyku.livefit.repository.projection.CreditPackageTotals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 對應 backend/controllers/admin.js 的業務邏輯（M3 教練後台、M6 營收） */
@Service
public class AdminCoachService {

    private static final List<String> MONTHS = List.of(
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december");

    private final UserRepository userRepository;
    private final CoachRepository coachRepository;
    private final CoachWithSkillRepository coachWithSkillRepository;
    private final CourseRepository courseRepository;
    private final CourseBookingRepository courseBookingRepository;
    private final CreditPackageRepository creditPackageRepository;

    public AdminCoachService(UserRepository userRepository,
                             CoachRepository coachRepository,
                             CoachWithSkillRepository coachWithSkillRepository,
                             CourseRepository courseRepository,
                             CourseBookingRepository courseBookingRepository,
                             CreditPackageRepository creditPackageRepository) {
        this.userRepository = userRepository;
        this.coachRepository = coachRepository;
        this.coachWithSkillRepository = coachWithSkillRepository;
        this.courseRepository = courseRepository;
        this.courseBookingRepository = courseBookingRepository;
        this.creditPackageRepository = creditPackageRepository;
    }

    // ===== M3-1 使用者升級為教練（免登入） =====

    @Transactional
    public CoachSignupResponse signupCoach(UUID userId, CoachSignupRequest request) {
        if (request == null
                || !ValidUtils.isValidString(request.description())
                || !ValidUtils.isInteger(request.experienceYears())
                || request.experienceYears() <= 0) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
        // profile_image_url 可不填，但有填就必須是 https 開頭
        if (ValidUtils.isValidString(request.profileImageUrl())
                && !ValidUtils.isHttpsUrl(request.profileImageUrl())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.USER_NOT_FOUND));
        if (user.isCoach()) {
            throw ApiException.conflict(ErrorMessages.ALREADY_COACH);
        }

        user.setRole(User.ROLE_COACH);
        User savedUser = userRepository.saveAndFlush(user);

        Coach coach = new Coach();
        coach.setUserId(userId);
        coach.setExperienceYears(request.experienceYears());
        coach.setDescription(request.description());
        coach.setProfileImageUrl(request.profileImageUrl());
        // saveAndFlush：讓 created_at / updated_at 在回應前就有值
        Coach savedCoach = coachRepository.saveAndFlush(coach);

        return CoachSignupResponse.of(savedUser, savedCoach);
    }

    // ===== M3-2 教練個人資料 =====

    @Transactional(readOnly = true)
    public CoachProfileResponse getProfile(UUID userId) {
        Coach coach = requireCoach(userId);
        return CoachProfileResponse.of(coach, findSkillIds(coach.getId()));
    }

    @Transactional
    public CoachProfileResponse updateProfile(UUID userId, CoachProfileRequest request) {
        if (request == null
                || !ValidUtils.isValidString(request.description())
                || !ValidUtils.isHttpsUrl(request.profileImageUrl())
                || !ValidUtils.isInteger(request.experienceYears())
                || request.experienceYears() <= 0
                || request.skillIds() == null
                || request.skillIds().isEmpty()
                // Node 版此處誤用 every，應為「任一無效即擋下」
                || request.skillIds().stream().anyMatch(java.util.Objects::isNull)) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }

        Coach coach = requireCoach(userId);
        coach.setExperienceYears(request.experienceYears());
        coach.setDescription(request.description());
        coach.setProfileImageUrl(request.profileImageUrl());
        coachRepository.save(coach);

        // 整批換掉技能關聯
        coachWithSkillRepository.deleteByCoachId(coach.getId());
        coachWithSkillRepository.flush();
        List<CoachWithSkill> newRelations = request.skillIds().stream()
                .distinct()
                .map(skillId -> new CoachWithSkill(coach.getId(), skillId))
                .toList();
        coachWithSkillRepository.saveAll(newRelations);

        return CoachProfileResponse.of(coach,
                newRelations.stream().map(CoachWithSkill::getSkillId).toList());
    }

    // ===== M3-3 課程管理 =====

    @Transactional(readOnly = true)
    public List<CoachCourseListItem> getCourses(UUID userId) {
        List<Course> courses = courseRepository.findByUserId(userId);
        if (courses.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> participants = courseBookingRepository
                .countParticipantsByCourseIds(courses.stream().map(Course::getId).toList())
                .stream()
                .collect(Collectors.toMap(CourseParticipantCount::getCourseId,
                        CourseParticipantCount::getParticipants));

        Instant now = Instant.now();
        return courses.stream()
                .map(course -> new CoachCourseListItem(
                        course.getId(),
                        course.getName(),
                        resolveStatus(course, now),
                        course.getStartAt(),
                        course.getEndAt(),
                        course.getMaxParticipants(),
                        course.getMeetingUrl(),
                        participants.getOrDefault(course.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public CoachCourseDetailResponse getCourseDetail(UUID userId, UUID courseId) {
        return courseRepository.findByIdAndUserIdWithSkill(courseId, userId)
                .map(CoachCourseDetailResponse::from)
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.COURSE_NOT_FOUND));
    }

    @Transactional
    public CoachCourseResponse createCourse(UUID userId, CoachCourseRequest request) {
        validateCourseRequest(request);

        Course course = new Course();
        course.setUserId(userId);
        applyCourseRequest(course, request);
        return CoachCourseResponse.from(courseRepository.saveAndFlush(course));
    }

    @Transactional
    public CoachCourseResponse updateCourse(UUID userId, UUID courseId, CoachCourseRequest request) {
        validateCourseRequest(request);

        Course course = courseRepository.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.COURSE_NOT_FOUND));

        applyCourseRequest(course, request);
        return CoachCourseResponse.from(courseRepository.saveAndFlush(course));
    }

    // ===== M6 月營收 =====

    @Transactional(readOnly = true)
    public CoachRevenueResponse getRevenue(UUID userId, String month) {
        int monthNumber = resolveMonth(month);

        if (!courseRepository.existsByUserId(userId)) {
            return CoachRevenueResponse.empty();
        }

        int year = Instant.now().atZone(ZoneOffset.UTC).getYear();
        long participants =
                courseBookingRepository.countDistinctParticipantsInMonth(userId, year, monthNumber);
        long courseCount =
                courseBookingRepository.countBookingsInMonth(userId, year, monthNumber);

        CreditPackageTotals totals = creditPackageRepository.findTotals();
        BigDecimal totalCredits = totals == null ? BigDecimal.ZERO : totals.getTotalCreditAmount();
        BigDecimal totalPrice = totals == null ? BigDecimal.ZERO : totals.getTotalPrice();

        long revenue = 0L;
        if (totalCredits != null && totalCredits.signum() > 0) {
            // 單堂均價 = 全站方案總價 ÷ 全站方案總堂數；營收無條件捨去
            BigDecimal perCreditPrice = totalPrice.divide(totalCredits, 10, RoundingMode.HALF_UP);
            revenue = perCreditPrice.multiply(BigDecimal.valueOf(courseCount))
                    .setScale(0, RoundingMode.FLOOR)
                    .longValue();
        }

        return CoachRevenueResponse.of(revenue, participants, courseCount);
    }

    // ===== 內部共用 =====

    private Coach requireCoach(UUID userId) {
        return coachRepository.findByUserId(userId)
                // Node 版在這裡沒處理 coach 為 null，會 500；改為明確的 400
                .orElseThrow(() -> ApiException.badRequest(ErrorMessages.COACH_NOT_FOUND));
    }

    private List<UUID> findSkillIds(UUID coachId) {
        return coachWithSkillRepository.findByCoachId(coachId)
                .stream()
                .map(CoachWithSkill::getSkillId)
                .toList();
    }

    private String resolveStatus(Course course, Instant now) {
        if (course.getEndAt() != null && course.getEndAt().isBefore(now)) {
            return CoachCourseListItem.STATUS_ENDED;
        }
        if (course.getStartAt() != null && course.getStartAt().isBefore(now)) {
            return CoachCourseListItem.STATUS_ONGOING;
        }
        return CoachCourseListItem.STATUS_NOT_STARTED;
    }

    private void validateCourseRequest(CoachCourseRequest request) {
        if (request == null
                || request.skillId() == null
                || !ValidUtils.isValidString(request.name())
                || !ValidUtils.isValidString(request.description())
                || !ValidUtils.isValidString(request.startAt())
                || !ValidUtils.isValidString(request.endAt())
                || !ValidUtils.isInteger(request.maxParticipants())
                || !ValidUtils.isHttpsUrl(request.meetingUrl())) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
    }

    private void applyCourseRequest(Course course, CoachCourseRequest request) {
        course.setSkillId(request.skillId());
        course.setName(request.name());
        course.setDescription(request.description());
        course.setStartAt(DateTimeUtils.parseOrThrow(request.startAt()));
        course.setEndAt(DateTimeUtils.parseOrThrow(request.endAt()));
        course.setMaxParticipants(request.maxParticipants());
        course.setMeetingUrl(request.meetingUrl());
    }

    private int resolveMonth(String month) {
        if (!ValidUtils.isValidString(month)) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
        int index = MONTHS.indexOf(month.trim().toLowerCase(Locale.ROOT));
        if (index < 0) {
            throw ApiException.badRequest(ErrorMessages.INVALID_FIELDS);
        }
        return index + 1;
    }
}
