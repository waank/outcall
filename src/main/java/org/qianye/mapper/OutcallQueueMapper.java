package org.qianye.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.qianye.entity.OutcallQueueDO;

import java.util.List;

@Mapper
public interface OutcallQueueMapper extends BaseMapper<OutcallQueueDO> {
    int batchInsert(@Param("list") List<OutcallQueueDO> list);
}
