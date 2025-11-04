package vn.flast.dao;
/**************************************************************************/
/*  app.java                                                              */
/**************************************************************************/
/*                       Tệp này là một phần của:                         */
/*                             Open CDP                                   */
/*                        https://flast.vn                                */
/**************************************************************************/
/* Bản quyền (c) 2025 - này thuộc về các cộng tác viên Flast Solution     */
/* (xem AUTHORS.md).                                                      */
/* Bản quyền (c) 2024-2025 Long Huu, Thành Trung                          */
/*                                                                        */
/* Bạn được quyền sử dụng phần mềm này miễn phí cho bất kỳ mục đích nào,  */
/* bao gồm sao chép, sửa đổi, phân phối, bán lại…                         */
/*                                                                        */
/* Chỉ cần giữ nguyên thông tin bản quyền và nội dung giấy phép này trong */
/* các bản sao.                                                           */
/*                                                                        */
/* Đội ngũ phát triển mong rằng phần mềm được sử dụng đúng mục đích và    */
/* có trách nghiệm                                                        */
/**************************************************************************/

import org.springframework.stereotype.Repository;
import vn.flast.models.UserProfile;
import vn.flast.utils.EntityQuery;
import java.util.List;

@Repository("userProfileDao")
public class UserProfileDaoImpl extends DaoImpl<Long, UserProfile> implements UserProfileDao {

    public UserProfileDaoImpl() {
        super(UserProfile.class);
    }

    @Override
    public UserProfile findById(int id) {
        return EntityQuery.create(entityManager, UserProfile.class).integerEqualsTo("id", id)
                .uniqueResult();
    }

    @Override
    public List<UserProfile> findAll() {
        return EntityQuery.create(entityManager, UserProfile.class).addAscendingOrderBy("type").list();
    }

    @Override
    public void updateLinkProfile(List<Integer> listPermissions, int userId) {
        if(listPermissions.isEmpty() || userId == 0) {
            return;
        }
        entityManager.createNativeQuery("DELETE FROM `user_link_profile` WHERE `user_id` =:uId").setParameter("uId", userId).executeUpdate();
        entityManager.flush();
        listPermissions.forEach(item -> {
            String strQ = "INSERT INTO `user_link_profile` (`user_id`, `user_profile_id`) VALUES (%s, %s)".formatted(userId, item);
            entityManager.createNativeQuery(strQ).executeUpdate();
        });
    }
}
