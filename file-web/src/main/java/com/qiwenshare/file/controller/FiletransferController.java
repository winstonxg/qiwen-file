package com.qiwenshare.file.controller;

import com.alibaba.fastjson.JSON;
import com.qiwenshare.common.operation.FileOperation;
import com.qiwenshare.common.util.PathUtil;
import com.qiwenshare.common.cbb.RestResult;
import com.qiwenshare.common.operation.ImageOperation;
import com.qiwenshare.file.api.IFileService;
import com.qiwenshare.file.api.IFiletransferService;
import com.qiwenshare.file.domain.FileBean;
import com.qiwenshare.file.domain.StorageBean;
import com.qiwenshare.file.domain.UserBean;
import org.apache.shiro.SecurityUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.List;

@RestController
@RequestMapping("/filetransfer")
public class FiletransferController {

    @Resource
    IFiletransferService filetransferService;
    @Resource
    IFileService fileService;




    /**
     * 批量删除图片
     *
     * @return
     */
    @RequestMapping(value = "/deleteimagebyids", method = RequestMethod.POST)
    @ResponseBody
    public String deleteImageByIds(@RequestBody String imageids) {
        RestResult<String> result = new RestResult<String>();
        List<Integer> imageidList = JSON.parseArray(imageids, Integer.class);
        UserBean sessionUserBean = (UserBean) SecurityUtils.getSubject().getPrincipal();

        long sessionUserId = sessionUserBean.getUserId();

//        List<ImageBean> imageBeanList = filetransferService.selectUserImageByIds(imageidList);
//        filetransferService.deleteUserImageByIds(imageidList);
        List<FileBean> fileList = fileService.selectFileListByIds(imageidList);
        fileService.deleteFileByIds(imageidList);
        long totalFileSize = 0;
        for (FileBean fileBean : fileList) {
            String imageUrl = PathUtil.getStaticPath() + fileBean.getFileUrl();
            String minImageUrl = imageUrl.replace("." + fileBean.getExtendName(), "_min." + fileBean.getExtendName());
            totalFileSize += FileOperation.getFileSize(imageUrl);
            FileOperation.deleteFile(imageUrl);
            FileOperation.deleteFile(minImageUrl);
        }
        StorageBean storageBean = filetransferService.selectStorageBean(new StorageBean(sessionUserId));
        if (storageBean != null){
            long updateFileSize = storageBean.getStorageSize() - totalFileSize;
            if (updateFileSize < 0){
                updateFileSize = 0;
            }
            storageBean.setStorageSize(updateFileSize);
            filetransferService.updateStorageBean(storageBean);

        }

        result.setData("删除文件成功");
        result.setSuccess(true);
        String resultJson = JSON.toJSONString(result);
        return resultJson;
    }

    /**
     * 删除图片
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/deleteimage", method = RequestMethod.POST)
    @ResponseBody
    public String deleteImage(HttpServletRequest request, @RequestBody FileBean fileBean) {
        RestResult<String> result = new RestResult<String>();
        UserBean sessionUserBean = (UserBean) SecurityUtils.getSubject().getPrincipal();
        long sessionUserId = sessionUserBean.getUserId();
        String imageUrl = PathUtil.getStaticPath() + fileBean.getFileUrl();
        String minImageUrl = imageUrl.replace("." + fileBean.getExtendName(), "_min." + fileBean.getExtendName());
        long fileSize = FileOperation.getFileSize(imageUrl);
        fileBean.setIsDir(0);
        //filetransferService.deleteImageById(fileBean);
        fileService.deleteFile(fileBean);

        FileOperation.deleteFile(imageUrl);
        FileOperation.deleteFile(minImageUrl);


        StorageBean storageBean = filetransferService.selectStorageBean(new StorageBean(sessionUserId));
        if (storageBean != null){
            long updateFileSize = storageBean.getStorageSize() - fileSize;
            if (updateFileSize < 0){
                updateFileSize = 0;
            }
            storageBean.setStorageSize(updateFileSize);
            filetransferService.updateStorageBean(storageBean);

        }

        String resultJson = JSON.toJSONString(result);
        return resultJson;
    }


    /**
     * 上传文件
     *
     * @param request
     * @return
     */
    @RequestMapping(value = "/uploadfile", method = RequestMethod.POST)
    @ResponseBody
    public String uploadFile(HttpServletRequest request, FileBean fileBean) {
        RestResult<String> restResult = new RestResult<String>();
        UserBean sessionUserBean = (UserBean) SecurityUtils.getSubject().getPrincipal();
        RestResult<String> operationCheckResult = new FileController().operationCheck();
        if (!operationCheckResult.isSuccess()){
            return JSON.toJSONString(operationCheckResult);
        }

        fileBean.setUserId(sessionUserBean.getUserId());

        filetransferService.uploadFile(request, fileBean);

        restResult.setSuccess(true);
        String resultJson = JSON.toJSONString(restResult);
        return resultJson;
    }
    /**
     * 下载文件
     *
     * @return
     */
    @RequestMapping(value = "/downloadfile", method = RequestMethod.GET)
    public String downloadFile(HttpServletResponse response, FileBean fileBean) {
        RestResult<String> restResult = new RestResult<>();
        String fileName = null;// 文件名
        try {
            fileName = new String(fileBean.getFileName().getBytes("utf-8"), "ISO-8859-1");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        if (fileName != null) {
            fileName = fileName + "." + fileBean.getExtendName();
            //设置文件路径
            File file = FileOperation.newFile(PathUtil.getStaticPath() + fileBean.getFileUrl());
            if (file.exists()) {
                response.setContentType("application/force-download");// 设置强制下载不打开
                response.addHeader("Content-Disposition", "attachment;fileName=" + fileName);// 设置文件名
                byte[] buffer = new byte[1024];
                FileInputStream fis = null;
                BufferedInputStream bis = null;
                try {
                    fis = new FileInputStream(file);
                    bis = new BufferedInputStream(fis);
                    OutputStream os = response.getOutputStream();
                    int i = bis.read(buffer);
                    while (i != -1) {
                        os.write(buffer, 0, i);
                        i = bis.read(buffer);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (bis != null) {
                        try {
                            bis.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (fis != null) {
                        try {
                            fis.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        return null;

    }


    /**
     * 获取存储信息
     *
     * @return
     */
    @RequestMapping(value = "/getstorage", method = RequestMethod.GET)
    @ResponseBody
    public RestResult<StorageBean> getStorage() {
        RestResult<StorageBean> restResult = new RestResult<StorageBean>();
        UserBean sessionUserBean = (UserBean) SecurityUtils.getSubject().getPrincipal();
        StorageBean storageBean = new StorageBean();
        if (FileController.isShareFile){
            storageBean.setUserId(2L);
        }else{
            storageBean.setUserId(sessionUserBean.getUserId());
        }

        StorageBean storage = filetransferService.selectStorageByUser(storageBean);
        restResult.setData(storage);
        restResult.setSuccess(true);
        return restResult;
    }



}
