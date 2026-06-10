package com.scorestv.user.dto;

import com.scorestv.user.Role;
import com.scorestv.user.User;

import java.time.LocalDate;
import java.time.Period;

public record UserResponse(
        Long id,
        String email,
        String displayName,
        Role role,
        LocalDate birthDate,
        Integer age,
        String country
) {
    public static UserResponse from(User user) {
        Integer age = user.getBirthDate() != null
                ? Period.between(user.getBirthDate(), LocalDate.now()).getYears()
                : null;
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getBirthDate(),
                age,
                user.getCountry()
        );
    }
}
