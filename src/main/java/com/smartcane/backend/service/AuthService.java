package com.smartcane.backend.service;

import com.smartcane.backend.entity.dto.LoginDTO;
import com.smartcane.backend.entity.vo.LoginVO;
import com.smartcane.backend.entity.vo.Result;

public interface AuthService {

    /** 账号密码登录，成功返回会话 token 与用户信息 */
    Result<LoginVO> login(LoginDTO dto);

    /** 登出：服务端立即删除会话，token 马上失效 */
    Result<Void> logout();

    /** 当前登录用户，前端刷新页面时用它恢复身份 */
    Result<LoginVO> currentUser();
}
