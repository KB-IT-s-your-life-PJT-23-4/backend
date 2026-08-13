package com.example.project.batch.pii;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PiiMigrationMapper {
    List<PiiMigrationUser> selectUsersAfter(
            @Param("afterId") long afterId,
            @Param("limit") int limit
    );

    List<PiiMigrationFamily> selectFamiliesAfter(
            @Param("afterId") long afterId,
            @Param("limit") int limit
    );

    int countEmailHmacCollision(
            @Param("userId") long userId,
            @Param("emailHmac") String emailHmac
    );

    int countPhoneHmacCollision(
            @Param("userId") long userId,
            @Param("phoneHmac") String phoneHmac
    );

    int updateUser(PiiMigrationUser user);

    int updateFamily(PiiMigrationFamily family);
}
