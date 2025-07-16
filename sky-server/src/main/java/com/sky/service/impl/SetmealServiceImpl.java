package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.annotation.AutoFill;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.exception.SetmealEnableFailedException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.result.PageResult;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;

    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐
     * @param setmealDTO
     */
    @Transactional
    public void save(SetmealDTO setmealDTO) {
        //向套餐表插入套餐信息
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);
        setmealMapper.insert(setmeal);

        Long setmealId = setmealMapper.getIdByName(setmeal.getName());
        log.info("套餐id：{}",setmealId);
        //获取套餐id并且插入到要添加的信息中
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        for(SetmealDish sd : setmealDishes){
            sd.setSetmealId(setmealId);
        }
        //一次性插入多条套餐菜品信息
        setmealDishMapper.insertBatch(setmealDishes);
    }


    /**
     * 分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        int pageNum = setmealPageQueryDTO.getPage();
        int pageSize = setmealPageQueryDTO.getPageSize();

        PageHelper.startPage(pageNum, pageSize);
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 删除套餐
     * @param ids
     */
    @Transactional
    public void delete(List<Long> ids){
        //判断是否有启售中的套餐
        for(Long id : ids){
            if(setmealMapper.getById(id).getStatus()==StatusConstant.ENABLE){
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        //删除套餐
        setmealMapper.delete(ids);

        //删除套餐关联的套餐菜品表
        setmealDishMapper.delete(ids);
    }

    /**
     * 根据id查询套餐信息
     * @param id
     * @return
     */
    public SetmealVO getById(Long id) {
        SetmealVO setmealVO = setmealMapper.getById(id);
        setmealVO.setSetmealDishes(setmealDishMapper.getBySetmealId(id));
        log.info("套餐信息：{}",setmealVO);
        return setmealVO;
    }

    /**
     * 修改套餐
     * @param setmealDTO
     */
    @Transactional
    public void update(SetmealDTO setmealDTO) {
        //传入数据拷贝到semeal对象中
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO,setmeal);

        //修改setmeal表中的数据
        setmealMapper.update(setmeal);

        //清空setmeal_dish表中对setmealId的数据
        setmealDishMapper.deleteBySetmealId(setmealDTO.getId());

        //如果有就重新加入setmeal_dish表中对setmealId的数据
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        if(setmealDishes!=null && setmealDishes.size()>0){
            for(SetmealDish setmealDish : setmealDishes){
                setmealDish.setSetmealId(setmealDTO.getId());
            }
            setmealDishMapper.insertBatch(setmealDishes);
        }
    }

    /**
     * 启售禁售套餐
     * @param status
     * @param id
     */
    public void startOrStop(Integer status, Long id) {

        //如果要启售套餐首先要判断套餐内的菜品是否全部启售
        if(status == StatusConstant.ENABLE){
            List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);
            if(setmealDishes!=null && setmealDishes.size()>0){
                for(SetmealDish sd : setmealDishes){
                    if(dishMapper.getById(sd.getDishId()).getStatus() == StatusConstant.DISABLE){
                        throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                    }
                }
            }
        }
        Setmeal setmeal = Setmeal.builder()
                .status(status)
                .id(id)
                .build();
        setmealMapper.startOrStop(setmeal);
    }

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    public List<Setmeal> list(Setmeal setmeal) {
        List<Setmeal> setmeals = setmealMapper.list(setmeal);
        return setmeals;
    }

    /**
     * 根据id查询菜品选项
     * @param id
     * @return
     */
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }
}
