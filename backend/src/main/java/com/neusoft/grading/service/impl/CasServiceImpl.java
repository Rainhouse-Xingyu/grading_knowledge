package com.neusoft.grading.service.impl;

import com.neusoft.grading.common.BizException;
import com.neusoft.grading.config.CasConfig;
import com.neusoft.grading.entity.Student;
import com.neusoft.grading.entity.Teacher;
import com.neusoft.grading.service.CasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import org.xml.sax.InputSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * CAS 统一身份认证服务实现
 *
 * 支持两种模式：
 * 1. mock=true（开发模式）：ticket 以 "stu-" 开头返回学生，"tea-" 开头返回教师
 * 2. mock=false（生产模式）：调用 CAS 3.0 /serviceValidate 接口验证 ticket，
 *    从 XML 响应中解析属性，根据 employeeType 字段区分学生/教师角色。
 *
 * CAS 3.0 响应格式示例：
 * <cas:serviceResponse>
 *   <cas:authenticationSuccess>
 *     <cas:user>2022001</cas:user>
 *     <cas:attributes>
 *       <cas:employeeType>student</cas:employeeType>
 *       <cas:name>张三</cas:name>
 *       ...
 *     </cas:attributes>
 *   </cas:authenticationSuccess>
 * </cas:serviceResponse>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CasServiceImpl implements CasService {

    private final CasConfig casConfig;
    private final RestTemplate restTemplate = new RestTemplate();

    // ========== CAS 属性名常量（与学校 CAS 下发字段对应） ==========
    private static final String ATTR_EMPLOYEE_TYPE = "employeeType";
    private static final String ATTR_NAME           = "name";
    private static final String ATTR_GENDER         = "gender";
    private static final String ATTR_DEPT_CODE      = "deptCode";
    private static final String ATTR_DEPT_NAME      = "deptName";
    private static final String ATTR_MAJOR_CODE     = "majorCode";
    private static final String ATTR_MAJOR_NAME     = "majorName";
    private static final String ATTR_CLASS_CODE     = "classCode";
    private static final String ATTR_CLASS_NAME     = "className";
    private static final String ATTR_TITLE_CODE     = "titleCode";
    private static final String ATTR_TITLE_NAME     = "titleName";

    /** 学生角色标识（与学校 CAS employeeType 值一致） */
    private static final String ROLE_STUDENT = "student";
    /** 教师角色标识 */
    private static final String ROLE_TEACHER = "teacher";

    // ==================== 公开接口 ====================

    @Override
    public Student validateAndExtractStudent(String ticket, String service) {
        if (casConfig.isMock()) {
            return mockValidateStudent(ticket);
        }
        // 生产模式：调用 CAS 3.0 验证
        Map<String, String> attrs = validateTicket(ticket, service);
        String role = attrs.getOrDefault(ATTR_EMPLOYEE_TYPE, "");
        if (!ROLE_STUDENT.equals(role)) {
            return null;
        }
        Student student = new Student();
        student.setStudentNo(attrs.getOrDefault("user", ""));
        student.setName(attrs.getOrDefault(ATTR_NAME, ""));
        student.setGender(attrs.get(ATTR_GENDER));
        student.setDeptCode(attrs.get(ATTR_DEPT_CODE));
        student.setDeptName(attrs.get(ATTR_DEPT_NAME));
        student.setMajorCode(attrs.get(ATTR_MAJOR_CODE));
        student.setMajorName(attrs.get(ATTR_MAJOR_NAME));
        student.setClassCode(attrs.get(ATTR_CLASS_CODE));
        student.setClassName(attrs.get(ATTR_CLASS_NAME));
        return student;
    }

    @Override
    public Teacher validateAndExtractTeacher(String ticket, String service) {
        if (casConfig.isMock()) {
            return mockValidateTeacher(ticket);
        }
        // 生产模式：调用 CAS 3.0 验证
        Map<String, String> attrs = validateTicket(ticket, service);
        String role = attrs.getOrDefault(ATTR_EMPLOYEE_TYPE, "");
        if (!ROLE_TEACHER.equals(role)) {
            return null;
        }
        Teacher teacher = new Teacher();
        teacher.setTeacherNo(attrs.getOrDefault("user", ""));
        teacher.setTeacherName(attrs.getOrDefault(ATTR_NAME, ""));
        teacher.setDeptCode(attrs.get(ATTR_DEPT_CODE));
        teacher.setDeptName(attrs.get(ATTR_DEPT_NAME));
        teacher.setTitleCode(attrs.get(ATTR_TITLE_CODE));
        teacher.setTitleName(attrs.get(ATTR_TITLE_NAME));
        return teacher;
    }

    @Override
    public String getCasLogoutUrl() {
        return casConfig.getLogoutUrl();
    }

    // ==================== CAS 3.0 真实验证 ====================

    /**
     * 调用 CAS 3.0 /serviceValidate 接口验证 ticket，返回解析后的属性 Map。
     * 属性 Map 包含 "user" 键表示登录用户名，其余为 CAS 下发的扩展属性。
     */
    private Map<String, String> validateTicket(String ticket, String service) {
        String serviceParam = URLEncoder.encode(service, StandardCharsets.UTF_8);
        String validateUrl = casConfig.getServerUrl()
                + "/serviceValidate?ticket=" + ticket
                + "&service=" + serviceParam;

        log.debug("CAS 3.0 验证请求: {}", validateUrl);

        String xml;
        try {
            xml = restTemplate.getForObject(validateUrl, String.class);
        } catch (Exception e) {
            log.error("CAS 服务不可达: {}", validateUrl, e);
            throw BizException.unauthorized("CAS 认证服务暂时不可用，请稍后重试");
        }

        if (xml == null || xml.isEmpty()) {
            throw BizException.unauthorized("CAS 返回为空");
        }

        return parseCasResponse(xml);
    }

    /**
     * 解析 CAS 3.0 XML 响应，提取 user 和 attributes。
     *
     * @param xml CAS 响应原始 XML 字符串
     * @return 属性 Map，key 为属性名，value 为属性值
     * @throws BizException 当 ticket 无效（authenticationFailure）时抛出
     */
    private Map<String, String> parseCasResponse(String xml) {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)));

            // 检查是否存在 authenticationFailure
            NodeList failures = doc.getElementsByTagName("cas:authenticationFailure");
            if (failures.getLength() > 0) {
                String code = ((Element) failures.item(0)).getAttribute("code");
                String msg = failures.item(0).getTextContent().trim();
                log.warn("CAS 认证失败: code={}, msg={}", code, msg);
                throw BizException.unauthorized("CAS 认证失败: " + msg);
            }

            // 提取 <cas:user>
            Map<String, String> attrs = new HashMap<>();
            NodeList users = doc.getElementsByTagName("cas:user");
            if (users.getLength() > 0) {
                attrs.put("user", users.item(0).getTextContent().trim());
            }

            // 提取 <cas:attributes> 下的子元素
            NodeList attrNodes = doc.getElementsByTagName("cas:attributes");
            if (attrNodes.getLength() > 0) {
                Element attrEl = (Element) attrNodes.item(0);
                NodeList children = attrEl.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element) {
                        Element child = (Element) children.item(i);
                        // 去掉 "cas:" 前缀得到属性名
                        String tagName = child.getTagName();
                        String key = tagName.contains(":")
                                ? tagName.substring(tagName.indexOf(":") + 1)
                                : tagName;
                        attrs.put(key, child.getTextContent().trim());
                    }
                }
            }

            log.debug("CAS 验证成功，解析到属性: {}", attrs.keySet());
            return attrs;

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("CAS XML 解析异常", e);
            throw BizException.unauthorized("CAS 响应解析失败");
        }
    }

    // ==================== 开发模拟 ====================

    private Student mockValidateStudent(String ticket) {
        if (ticket == null || !ticket.startsWith("stu-")) {
            return null;
        }
        String userNo = ticket.replace("stu-", "");
        Student student = new Student();
        student.setStudentNo(userNo);
        student.setName("模拟学生-" + userNo);
        student.setGender("男");
        student.setDeptCode("CS001");
        student.setDeptName("计算机科学与技术学院");
        student.setMajorCode("SE001");
        student.setMajorName("软件工程");
        student.setClassCode("SE202201");
        student.setClassName("软件工程2201班");
        return student;
    }

    private Teacher mockValidateTeacher(String ticket) {
        if (ticket == null || !ticket.startsWith("tea-")) {
            return null;
        }
        String userNo = ticket.replace("tea-", "");
        Teacher teacher = new Teacher();
        teacher.setTeacherNo(userNo);
        teacher.setTeacherName("模拟教师-" + userNo);
        teacher.setDeptCode("CS001");
        teacher.setDeptName("计算机科学与技术学院");
        teacher.setTitleCode("P01");
        teacher.setTitleName("副教授");
        return teacher;
    }
}
