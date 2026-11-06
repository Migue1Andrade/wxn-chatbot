package com.wxn.chatbot.department.mapper;

import com.wxn.chatbot.department.dto.DepartmentDtos;
import com.wxn.chatbot.department.entity.Department;

public final class DepartmentMapper {

    private DepartmentMapper() {}

    public static DepartmentDtos.Summary summary(Department d) {
        return new DepartmentDtos.Summary(d.getId(), d.getName(), d.getColor());
    }

    public static DepartmentDtos.Response response(Department d, long count) {
        return new DepartmentDtos.Response(
            d.getId(),
            d.getName(),
            d.getColor(),
            count,
            d.getDescription(),
            d.getStatus(),
            d.getCreatedAt(),
            d.getUpdatedAt(),
            d.getVersion()
        );
    }
}
