package com.neusoft.grading.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.neusoft.grading.entity.LocalUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 本地登录用户 Mapper
 */
@Mapper
public interface LocalUserMapper extends BaseMapper<LocalUser> {
}
