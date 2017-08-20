package com.mmall.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Created by 81975 on 2017/8/19.
 */
public interface IFileService {
    String upload(MultipartFile file, String path);
}
