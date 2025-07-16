package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.entity.Setmeal;
import com.sky.enumeration.OperationType;
import com.sky.vo.DishItemVO;
import com.sky.vo.SetmealVO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SetmealMapper {

    /**
     * 根据分类id查询套餐的数量
     * @param id
     * @return
     */
    @Select("select count(id) from setmeal where category_id = #{categoryId}")
    Integer countByCategoryId(Long id);

    /**
     * 新增套餐
     * @param setmeal
     */
    @AutoFill(OperationType.INSERT)
    @Insert("insert into setmeal (category_id,name,price,status,description,image,create_time,update_time,create_user,update_user) "+
           "values "+
           "(#{categoryId},#{name},#{price},#{status},#{description},#{image},#{createTime},#{updateTime},#{createUser},#{updateUser})")
    void insert(Setmeal setmeal);

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    Page<SetmealVO> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 删除套餐
     * @param ids
     */
    void delete(List<Long> ids);

    /**
     * 根据套餐名称获取套餐id
     * @param name
     * @return
     */
    @Select("select id from setmeal where name = #{name}")
    Long getIdByName(String name);

    /**
     * 根据套餐id获取套餐信息
     * @param id
     * @return
     */
    @Select("select s.*,c.name categoryName from setmeal s left join category c on s.category_id=c.id where s.id = #{id}")
    SetmealVO getById(Long id);

    /**
     * 修改套餐
     * @param setmeal
     */
    @AutoFill(OperationType.UPDATE)
    @Update("update setmeal set category_id=#{categoryId},name=#{name},price=#{price},description=#{description},image=#{image},"+
           "update_time=#{updateTime},update_user=#{updateUser} where id=#{id}")
    void update(Setmeal setmeal);

    /**
     * 启售禁售套餐
     * @param setmeal
     */
    @AutoFill(OperationType.UPDATE)
    @Update("update setmeal set status=#{status},update_time=#{updateTime},update_user=#{updateUser} where id=#{id}")
    void startOrStop(Setmeal setmeal);

    /**
     * 条件查询
     * @param setmeal
     * @return
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * 根据套餐id查询菜品信息
     * @param id
     * @return
     */
    @Select("select sd.name, sd.copies, d.image, d.description " +
            "from setmeal_dish sd left join dish d on sd.dish_id = d.id " +
            "where sd.setmeal_id = #{setmealId}")
    List<DishItemVO> getDishItemBySetmealId(Long id);
}
