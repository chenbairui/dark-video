package jit.wxs.dv.controller;

import com.baomidou.mybatisplus.mapper.EntityWrapper;
import com.baomidou.mybatisplus.plugins.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import jit.wxs.dv.convert.ContentBOConvert;
import jit.wxs.dv.convert.ContentVOConvert;
import jit.wxs.dv.domain.bo.ContentBO;
import jit.wxs.dv.domain.bo.PictureBO;
import jit.wxs.dv.domain.bo.VideoBO;
import jit.wxs.dv.domain.entity.*;
import jit.wxs.dv.domain.enums.CategoryLevelEnum;
import jit.wxs.dv.domain.enums.ResultEnum;
import jit.wxs.dv.domain.enums.RoleEnum;
import jit.wxs.dv.domain.vo.ContentVO;
import jit.wxs.dv.domain.vo.ResultVO;
import jit.wxs.dv.domain.vo.TreeVO;
import jit.wxs.dv.handler.CustomException;
import jit.wxs.dv.service.*;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * 页面展示Controller
 * @author jitwxs
 * @since 2018/10/7 22:12
 */
@Api(tags = {"页面管理"})
@Validated
@Controller
public class PageController {
    @Autowired
    private DvCategoryService categoryService;
    @Autowired
    private DvContentService contentService;
    @Autowired
    private DvContentAffixService contentAffixService;
    @Autowired
    private DvContentCommentService contentCommentService;
    @Autowired
    private DvContentLookLaterService lookLaterService;
    @Autowired
    private DvContentLookHistoryService lookHistoryService;
    @Autowired
    private ESService esService;
    @Autowired
    private ContentVOConvert contentVOConvert;
    @Autowired
    private ContentBOConvert contentBOConvert;

    @ApiOperation(value= "首页面")
    @GetMapping(value = "/")
    public String showIndex(@ApiIgnore ModelMap map) {
        // 导航数据
        List<TreeVO> navCategory = categoryService.listNavCategory();
        map.addAttribute("navCategory", navCategory);

        return "index";
    }

    @ApiOperation(value= "登录页面")
    @GetMapping(value = "/login")
    public String showLogin() {
        return "login";
    }

    @ApiOperation(value= "错误信息")
    @GetMapping(value = "/auth/error")
    public String loginError(@ApiIgnore HttpServletRequest request, @ApiIgnore ModelMap map) {
        String errorMsg;
        // 如果Spring Security中有异常，输出
        AuthenticationException exception =
                (AuthenticationException)request.getSession().getAttribute("SPRING_SECURITY_LAST_EXCEPTION");
        if(exception != null) {
            errorMsg = exception.toString();
        } else {
            // 其次从ERR_MSG中取
            Object obj = request.getAttribute("ERR_MSG");
            if(obj instanceof String) {
                errorMsg = (String)obj;
            } else if(obj instanceof ResultVO) {
                errorMsg = ((ResultVO) obj).getMessage();
            } else if(obj instanceof ResultEnum) {
                errorMsg = ((ResultEnum) obj).getMessage();
            } else {
                errorMsg = ResultEnum.OTHER_ERROR.getMessage();
            }
        }

        map.addAttribute("errorMsg", errorMsg);
        return "error";
    }

    @ApiOperation(value= "动态页面")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", name="current", value="当前页，默认为1"),
            @ApiImplicitParam(paramType = "query", name="size", value="页面容量，默认为10")
    })
    @GetMapping("/follow")
    public String showFollow(@RequestParam(defaultValue = "1") Integer current,
                             @RequestParam(defaultValue = "10") Integer size,
                             @ApiIgnore ModelMap map) {
        String username = ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername();
        Page<DvContent> selectPage = contentService.selectPage(new Page<>(current, size, "create_date", false));
        Page<ContentVO> page1 = new Page<>();
        BeanUtils.copyProperties(selectPage, page1);

        List<ContentVO> records1 = new ArrayList<>(selectPage.getSize() + 1);
        for(DvContent content : selectPage.getRecords()) {
            ContentVO contentVO = contentVOConvert.convert(content);
            // 判断是否加入稍后再看
            if(lookLaterService.hasExist(contentVO.getId(), username)) {
                contentVO.setHasLookAfter(true);
            } else {
                contentVO.setHasLookAfter(false);
            }
            records1.add(contentVO);
        }
        page1.setRecords(records1);

        map.addAttribute("page", page1);
        return "follow";
    }

    @ApiOperation(value= "稍后再看页面")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", name="current", value="当前页，默认为1"),
            @ApiImplicitParam(paramType = "query", name="size", value="页面容量，默认为10")
    })
    @GetMapping("/later")
    public String showLater(@RequestParam(defaultValue = "1") Integer current,
                                @RequestParam(defaultValue = "10") Integer size,
                                @ApiIgnore ModelMap map) {
        String username = ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername();

        Page<DvContentLookLater> page = lookLaterService.selectPage(
                new Page<>(current, size, "create_date", false),
                new EntityWrapper<DvContentLookLater>().eq("username", username));


        List<DvContentLookLater> records = page.getRecords();
        for(DvContentLookLater lookLater : records) {
            DvContent content = contentService.selectById(lookLater.getContentId());
            ContentVO contentVO = contentVOConvert.convert(content);
            lookLater.setContentVO(contentVO);
        }


        map.addAttribute("page", page);
        return "later";
    }

    @ApiOperation(value= "播放历史页面")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", name="current", value="当前页，默认为1"),
            @ApiImplicitParam(paramType = "query", name="size", value="页面容量，默认为10")
    })
    @GetMapping("/history")
    public String showHistory(@RequestParam(defaultValue = "1") Integer current,
                                @RequestParam(defaultValue = "10") Integer size,
                                @ApiIgnore ModelMap map) {
        String username = ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername();

        Page<DvContentLookHistory> page = lookHistoryService.selectPage(
                new Page<>(current, size, "look_date", false),
                new EntityWrapper<DvContentLookHistory>().eq("username", username));


        List<DvContentLookHistory> records = page.getRecords();
        for(DvContentLookHistory lookHistory : records) {
            DvContent content = contentService.selectById(lookHistory.getContentId());
            ContentVO contentVO = contentVOConvert.convert(content);
            lookHistory.setContentVO(contentVO);
        }

        map.addAttribute("page", page);
        return "history";
    }

    @ApiOperation(value= "管理中心页面")
    @GetMapping("/manager")
    public String showManager(@ApiIgnore HttpSession session) {
        session.setAttribute("sessionId", session.getId());

        Iterator<? extends GrantedAuthority> iterator = SecurityContextHolder.getContext().getAuthentication().getAuthorities().iterator();
        while (iterator.hasNext()) {
            String authority = iterator.next().getAuthority();
            if (RoleEnum.ROLE_USER.getMessage().equals(authority) || RoleEnum.ROLE_VIP.getMessage().equals(authority)) {
                return "user/manager";
            } else if(RoleEnum.ROLE_ADMIN.getMessage().equals(authority)) {

                return "admin/manager";
            }
        }

        return "login";
    }

    @ApiOperation(value= "类别页面")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", name="current", value="当前页，默认为1"),
            @ApiImplicitParam(paramType = "query", name="size", value="页面容量，默认为16"),
            @ApiImplicitParam(paramType = "query", name="sort", value="排序字段，默认为创建时间"),
            @ApiImplicitParam(paramType = "query", name="isAsc", value="排序方式true/false，默认为true升序")
    })
    @GetMapping("/category/{categoryId}")
    public String showCategory(@PathVariable String categoryId,
                               @RequestParam(defaultValue = "1") Integer current,
                               @RequestParam(defaultValue = "16") Integer size,
                               @RequestParam(defaultValue = "create_date") String sort,
                               @RequestParam(defaultValue = "true") Boolean isAsc,
                               @ApiIgnore ModelMap map) throws CustomException {
        DvCategory category = categoryService.selectById(categoryId);

        if(category == null) {
            throw new CustomException(ResultEnum.CATEGORY_NOT_EXIST);
        }

        // 获取目录级别
        CategoryLevelEnum levelEnum = category.getParentId() == null ? CategoryLevelEnum.FIRST : CategoryLevelEnum.SECOND;
        // 读取内容
        Page<ContentVO> contentPage = contentService.pageByCategory(levelEnum, categoryId, new Page<>(current, size, sort, isAsc));

        // 1级目录ID
        String firstCategoryId;

        // 读取该分类所属一级分类下所有二级分类列表
        List<DvCategory> secondCategories;
        if(category.getParentId() == null) {
            secondCategories = categoryService.listCategory(categoryId);
            firstCategoryId = categoryId;
        } else {
            secondCategories = categoryService.listCategory(category.getParentId());
            firstCategoryId = category.getParentId();
        }

        // 获取类别导航头
        List<TreeVO> navCategory = categoryService.listNavCategory();

        map.addAttribute("firstCategoryId", firstCategoryId);
        map.addAttribute("navCategory", navCategory);
        map.addAttribute("category", category);
        map.addAttribute("contents", contentPage);
        map.addAttribute("secondCategories", secondCategories);

        return "category";
    }

    @ApiOperation(value= "内容页面")
    @ApiImplicitParam(paramType = "path", name="contentId", value="内容ID", required = true)
    @GetMapping("/content/{contentId}")
    public String showContent(@PathVariable String contentId, @ApiIgnore ModelMap map) throws CustomException{
        // 判断合法
        DvContent content = contentService.selectById(contentId);
        if(content == null) {
            throw new CustomException(ResultEnum.CONTENT_NOT_EXIST);
        }

        String username = ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUsername();
        // 记录点击数
        contentService.click(contentId, 1);
        // 记录历史纪录
        lookHistoryService.addHistory(contentId, username);

        map.put("content", contentVOConvert.convert(content));
        map.put("type", content.getType());

        List<VideoBO> videoBOS = new LinkedList<>();
        List<PictureBO> pictureBOS = new LinkedList<>();
        List<ContentBO> contentBOS = new LinkedList<>();
        // 判断类型
        switch (content.getType()) {
            case "video":
                VideoBO videoBO = contentBOConvert.convert2Video(content);
                videoBOS.add(videoBO);
                map.put("videoBO", videoBOS);
                break;
            case "picture":
                PictureBO pictureBO = contentBOConvert.convert2Picture(content);
                pictureBOS.add(pictureBO);
                map.put("pictureBO", pictureBOS);
            case "music":
            case "page":
                ContentBO contentBO = contentBOConvert.convert2BO(content);
                contentBOS.add(contentBO);
                map.put("contentBO",contentBOS);
                break;
            case "dir":
                List<DvContentAffix> videoAffixes =
                        contentAffixService.selectList(new EntityWrapper<DvContentAffix>()
                                .eq("content_id", contentId)
                                .eq("type", "video")
                                .orderBy("name", true));
                List<DvContentAffix> pictureAffixes =
                        contentAffixService.selectList(new EntityWrapper<DvContentAffix>()
                                .eq("content_id", contentId)
                                .eq("type", "picture")
                                .orderBy("name", true));
                List<DvContentAffix> otherAffixes =
                        contentAffixService.selectList(new EntityWrapper<DvContentAffix>()
                                .eq("content_id", contentId)
                                .ne("type", "video")
                                .orderBy("name", true));

                videoBOS = contentBOConvert.convertAffix2Video(videoAffixes, content.getCreateDate(), content.getAuthor());
                pictureBOS = contentBOConvert.convertAffix2Picture(pictureAffixes);
                contentBOS = contentBOConvert.convertAffix2BO(otherAffixes, content.getCreateDate(), content.getAuthor());
                map.put("videoBO", videoBOS);
                map.put("pictureBO", pictureBOS);
                map.put("contentBO",contentBOS);
                break;
            default:
                break;
        }

        // 读取评论
        List<DvContentComment> comments = contentCommentService.listByContentId(contentId);
        map.put("comments",comments);
        map.put("commentCount",comments.size());

        // 读取推荐视频
        List<ContentVO> recommends = contentService.listRecommend(content, 10);
        map.put("recommends", recommends);

        // 获取类别导航头
        List<TreeVO> navCategory = categoryService.listNavCategory();
        map.addAttribute("navCategory", navCategory);

        return "content";
    }

    @ApiOperation(value= "搜索页面")
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "query", name="keyword", value="搜索关键字", required = true),
            @ApiImplicitParam(paramType = "query", name="current", value="当前页，默认为1"),
            @ApiImplicitParam(paramType = "query", name="size", value="页面容量，默认为10")
    })
    @GetMapping("/content/search")
    public String search(@NotBlank(message = "搜索关键字不能为空") String keyword, @ApiIgnore ModelMap map,
                         @RequestParam(defaultValue = "1") Integer current,
                         @RequestParam(defaultValue = "10") Integer size) {

        Page<ESContent> page = esService.searchContent(keyword, current, size);

        map.addAttribute("keyword", keyword);
        map.addAttribute("page", page);

        return "search";
    }
}
