package com.example.familytree.service;

import com.example.familytree.domain.DomainModels.Gender;
import com.example.familytree.service.AccessService.BadRequestException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Types;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

@Service
public class ProfileService {
    private final JdbcClient jdbc;
    private final AccessService access;
    private final ProfilePortraitStorage portraits;
    private static final DateTimeFormatter BIRTH_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    public ProfileService(JdbcClient jdbc, AccessService access, ProfilePortraitStorage portraits) {
        this.jdbc = jdbc; this.access = access; this.portraits = portraits;
    }

    public record ProfileDto(String username, String displayName, String birthDate, Integer age,
                             Gender gender, boolean hasPortrait, String portraitVersion,
                             String spouseStatus, String education, String biography) {}
    private record ProfileRow(String username, String displayName, LocalDate birthDate, Gender gender,
                              String portraitName, String portraitVersion,
                              String spouseStatus, String education, String biography) {
        ProfileDto dto() {
            return new ProfileDto(username, displayName == null ? "" : displayName,
                    birthDate == null ? null : BIRTH_DATE.format(birthDate),
                    birthDate == null ? null : Period.between(birthDate, LocalDate.now()).getYears(),
                    gender, portraitName != null, portraitName == null ? null : portraitVersion,
                    spouseStatus == null ? "" : spouseStatus, education == null ? "" : education,
                    biography == null ? "" : biography);
        }
    }

    @Transactional(readOnly = true)
    public ProfileDto get(Authentication auth) {
        return row(access.current(auth).getId(), false).dto();
    }

    @Transactional
    public ProfileDto update(String displayName, String birthDate, Gender gender, Authentication auth) {
        return update(displayName, birthDate, gender, null, null, null, auth);
    }

    @Transactional
    public ProfileDto update(String displayName, String birthDate, Gender gender,
                             String spouseStatus, String education, String biography, Authentication auth) {
        long id = access.current(auth).getId();
        row(id, true);
        access.current(auth); // Revalidate after waiting for an administration/account row lock.
        if (displayName == null || displayName.length() > 160) throw new BadRequestException("displayName 不能为空值且最长 160 字符（允许空字符串）");
        if (gender == null) throw new BadRequestException("gender 不能为空");
        validateLength("spouseStatus", spouseStatus, 120);
        validateLength("education", education, 120);
        validateLength("biography", biography, 5000);
        LocalDate birth = parseBirthDate(birthDate);
        // Missing/null details preserve existing values for old clients; an explicit empty string clears them.
        // Only editable profile columns; no entity merge can alter role/binding/other users.
        jdbc.sql("update users set display_name=:name,birth_date=:birth,gender=:gender,"
                        + "spouse_status=coalesce(:spouseStatus,spouse_status),education=coalesce(:education,education),"
                        + "biography=coalesce(:biography,biography) where id=:id and enabled=true and deleted=false")
                .param("name", displayName.strip()).param("birth", birth, Types.DATE).param("gender", gender.name())
                .param("spouseStatus", spouseStatus, Types.VARCHAR).param("education", education, Types.VARCHAR)
                .param("biography", biography, Types.VARCHAR).param("id", id).update();
        return row(id, false).dto();
    }

    private static void validateLength(String field, String value, int maximum) {
        if (value != null && value.length() > maximum) throw new BadRequestException(field + " 最长 " + maximum + " 字符");
    }

    static LocalDate parseBirthDate(String value) {
        if (value == null || value.isEmpty()) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new BadRequestException("birthDate 必须是合法的 YYYY-MM-DD 或 null");
        try {
            var date = LocalDate.parse(value, BIRTH_DATE);
            if (date.getYear() < 1 || date.isAfter(LocalDate.now())) throw new BadRequestException("生日必须为非未来的合法日期");
            return date;
        } catch (DateTimeParseException e) { throw new BadRequestException("birthDate 必须是合法的 YYYY-MM-DD 或 null"); }
    }

    @Transactional
    public ProfileDto uploadPortrait(MultipartFile file, Authentication auth) {
        long id = access.current(auth).getId();
        var previous = row(id, true);
        access.current(auth);
        var stored = portraits.store(id, file);
        // Keep the old file until COMMIT; remove a new file on rollback, including commit failures.
        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { portraits.deleteQuietly(id, previous.portraitName()); }
                @Override public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) portraits.deleteQuietly(id, stored.name());
                }
            });
        } catch (RuntimeException e) {
            portraits.deleteQuietly(id, stored.name()); throw e;
        }
        jdbc.sql("update users set portrait_stored_name=:name,portrait_version=:version where id=:id and enabled=true and deleted=false")
                .param("name", stored.name()).param("version", stored.version()).param("id", id).update();
        return row(id, false).dto();
    }

    @Transactional
    public ProfilePortraitStorage.Portrait portrait(Authentication auth) {
        long id = access.current(auth).getId();
        // Hold the same row lock until bytes are read, so replacement cannot delete an in-flight read.
        var current = row(id, true);
        access.current(auth);
        return portraits.read(id, current.portraitName());
    }

    private ProfileRow row(long id, boolean lock) {
        return jdbc.sql("select username,display_name,birth_date,gender,portrait_stored_name,portrait_version,spouse_status,education,biography from users where id=:id and enabled=true and deleted=false" + (lock ? " for update" : ""))
                .param("id", id).query((rs, n) -> new ProfileRow(rs.getString("username"), rs.getString("display_name"),
                        rs.getObject("birth_date", LocalDate.class), Gender.valueOf(rs.getString("gender")),
                        rs.getString("portrait_stored_name"), rs.getString("portrait_version"),
                        rs.getString("spouse_status"), rs.getString("education"), rs.getString("biography")))
                .optional().orElseThrow(() -> new DisabledException("账号不可用"));
    }
}
