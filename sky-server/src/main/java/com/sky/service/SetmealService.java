package com.sky.service;

import com.sky.dto.SetmealDTO;
import org.springframework.stereotype.Service;


/**
 * 新增套餐
 */
public interface SetmealService {
    void save(SetmealDTO setmealDTO);
}
