package com.campuspath.gui;

import com.campuspath.dao.impl.PostgresAdminReportDAO;
import com.campuspath.dao.impl.PostgresApplicationDAO;
import com.campuspath.dao.impl.PostgresApplicationStatusHistoryDAO;
import com.campuspath.dao.impl.PostgresAssessmentDAO;
import com.campuspath.dao.impl.PostgresCompanyDAO;
import com.campuspath.dao.impl.PostgresPlacementDriveDAO;
import com.campuspath.dao.impl.PostgresPlacementOutcomeDAO;
import com.campuspath.dao.impl.PostgresProbabilityHistoryDAO;
import com.campuspath.dao.impl.PostgresSkillDAO;
import com.campuspath.dao.impl.PostgresStudentDAO;
import com.campuspath.dao.impl.PostgresUserDAO;
import com.campuspath.model.User;
import com.campuspath.service.AdminReportService;
import com.campuspath.service.ApplicationService;
import com.campuspath.service.AssessmentService;
import com.campuspath.service.AuthenticationService;
import com.campuspath.service.CompanyService;
import com.campuspath.service.HistoricalEstimatorService;
import com.campuspath.service.PlacementDriveService;
import com.campuspath.service.SkillService;
import com.campuspath.service.StudentService;

import java.util.List;
import java.util.Set;

public final class CampusPathServices {
    public static final List<String> CONTROLLED_DEPARTMENTS = List.of(
            "Computer Science",
            "Information Technology",
            "Electronics and Communication",
            "Electrical Engineering",
            "Mechanical Engineering",
            "Civil Engineering",
            "Data Science",
            "Artificial Intelligence and Machine Learning"
    );

    private final PostgresUserDAO users = new PostgresUserDAO();
    private final PostgresStudentDAO students = new PostgresStudentDAO();
    private final PostgresCompanyDAO companies = new PostgresCompanyDAO();
    private final PostgresPlacementDriveDAO drives = new PostgresPlacementDriveDAO();
    private final PostgresSkillDAO skills = new PostgresSkillDAO();
    private final PostgresAssessmentDAO assessments = new PostgresAssessmentDAO();
    private final PostgresApplicationDAO applications = new PostgresApplicationDAO();
    private final PostgresApplicationStatusHistoryDAO history =
            new PostgresApplicationStatusHistoryDAO();
    private final PostgresPlacementOutcomeDAO outcomes = new PostgresPlacementOutcomeDAO();
    private final PostgresProbabilityHistoryDAO probabilities =
            new PostgresProbabilityHistoryDAO();
    private final PostgresAdminReportDAO reports = new PostgresAdminReportDAO();
    private final AuthenticationService authentication = new AuthenticationService(
            users, Set.copyOf(CONTROLLED_DEPARTMENTS)
    );

    public AuthenticationService authentication() {
        return authentication;
    }

    public StudentService students(User actor) {
        return new StudentService(students, Set.copyOf(CONTROLLED_DEPARTMENTS), actor);
    }

    public CompanyService companies() {
        return new CompanyService(companies);
    }

    public PlacementDriveService drives() {
        return new PlacementDriveService(drives, companies);
    }

    public SkillService skills() {
        return new SkillService(skills);
    }

    public AssessmentService assessments() {
        return new AssessmentService(assessments, students);
    }

    public ApplicationService applications() {
        return new ApplicationService(
                students, drives, applications, history, outcomes
        );
    }

    public HistoricalEstimatorService estimator() {
        return new HistoricalEstimatorService(
                students, drives, assessments, outcomes, probabilities
        );
    }

    public AdminReportService reports(User actor) {
        return new AdminReportService(reports, actor);
    }
}
