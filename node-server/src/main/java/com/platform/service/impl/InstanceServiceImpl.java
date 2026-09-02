package com.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.platform.common.BizException;
import com.platform.common.ResultCode;
import com.platform.dto.DeregisterRequest;
import com.platform.dto.HeartbeatRequest;
import com.platform.dto.RegisterRequest;
import com.platform.entity.Instance;
import com.platform.entity.InstanceLog;
import com.platform.mapper.InstanceLogMapper;
import com.platform.mapper.InstanceMapper;
import com.platform.service.InstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class InstanceServiceImpl implements InstanceService {

    private final InstanceMapper instanceMapper;
    private final InstanceLogMapper instanceLogMapper;

    /* ============================================================
     *                       Agent → 平台
     * ============================================================ */

    @Override
    @Transactional
    public Instance register(RegisterRequest req) {
        LocalDateTime now = LocalDateTime.now();

        Instance exist = instanceMapper.selectOne(
                new LambdaQueryWrapper<Instance>().eq(Instance::getInstanceId, req.getInstanceId()).last("LIMIT 1"));

        if (exist == null) {
            Instance ins = new Instance();
            ins.setInstanceId(req.getInstanceId());
            ins.setName(req.getName());
            ins.setIp(req.getIp());
            ins.setPort(req.getPort() == null ? 1880 : req.getPort());
            ins.setPlatform(req.getPlatform());
            ins.setArch(req.getArch());
            ins.setNodeVersion(req.getNodeVersion());
            ins.setNodeRedVersion(req.getNodeRedVersion());
            ins.setStartTime(req.getStartTime());
            ins.setRegisterTime(now);
            ins.setLastHeartbeatTime(now);
            ins.setStatus("online");
            ins.setBindStatus("unbound");
            ins.setCreateTime(now);
            ins.setUpdateTime(now);
            instanceMapper.insert(ins);
            saveLog(req.getInstanceId(), "register", "新实例注册: " + req.getName() + " (" + req.getIp() + ")");
            log.info("[register] 新实例 instanceId={} name={} ip={}", req.getInstanceId(), req.getName(), req.getIp());
            return ins;
        }

        // 已存在 → 幂等更新：刷新运行时信息 + 心跳时间 + 状态置为在线
        exist.setName(req.getName());
        exist.setIp(req.getIp());
        exist.setPort(req.getPort() == null ? exist.getPort() : req.getPort());
        exist.setPlatform(req.getPlatform());
        exist.setArch(req.getArch());
        exist.setNodeVersion(req.getNodeVersion());
        exist.setNodeRedVersion(req.getNodeRedVersion());
        exist.setStartTime(req.getStartTime());
        exist.setLastHeartbeatTime(now);
        exist.setRegisterTime(now);
        exist.setStatus("online");
        exist.setUpdateTime(now);
        instanceMapper.updateById(exist);
        saveLog(req.getInstanceId(), "register", "实例重新注册");
        log.info("[register] 重新注册 instanceId={}", req.getInstanceId());
        return exist;
    }

    @Override
    @Transactional
    public void heartbeat(HeartbeatRequest req) {
        Instance exist = instanceMapper.selectOne(
                new LambdaQueryWrapper<Instance>().eq(Instance::getInstanceId, req.getInstanceId()).last("LIMIT 1"));
        if (exist == null) {
            // 实例不存在 → Agent 会重新注册
            saveLog(req.getInstanceId(), "heartbeat", "心跳发现实例不存在");
            throw new BizException(ResultCode.INSTANCE_NOT_FOUND);
        }
        if ("deregistered".equals(exist.getStatus())) {
            // 实例已被注销 → Agent 会重新注册（注册接口会自动将其从 deregistered 恢复为 online）
            saveLog(req.getInstanceId(), "heartbeat", "心跳发现实例已注销");
            throw new BizException(ResultCode.INSTANCE_DEREGISTERED);
        }

        LocalDateTime now = LocalDateTime.now();
        Instance upd = new Instance();
        upd.setId(exist.getId());
        upd.setLastHeartbeatTime(now);
        upd.setStatus("online");
        upd.setUpdateTime(now);
        instanceMapper.updateById(upd);
        // 心跳不写日志，避免日志爆炸；仅在状态变化/异常时记
    }

    @Override
    @Transactional
    public void deregister(DeregisterRequest req) {
        Instance exist = instanceMapper.selectOne(
                new LambdaQueryWrapper<Instance>().eq(Instance::getInstanceId, req.getInstanceId()).last("LIMIT 1"));
        if (exist == null) {
            // 不存在视为成功（幂等）
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Instance upd = new Instance();
        upd.setId(exist.getId());
        upd.setStatus("deregistered");
        upd.setUpdateTime(now);
        instanceMapper.updateById(upd);
        saveLog(req.getInstanceId(), "deregister", "Agent 主动注销");
        log.info("[deregister] instanceId={}", req.getInstanceId());
    }

    /* ============================================================
     *                       管理后台
     * ============================================================ */

    @Override
    public List<Instance> listInstances(String status, String bindStatus, String keyword) {
        LambdaQueryWrapper<Instance> w = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            w.eq(Instance::getStatus, status);
        }
        if (bindStatus != null && !bindStatus.isEmpty()) {
            w.eq(Instance::getBindStatus, bindStatus);
        }
        if (keyword != null && !keyword.isEmpty()) {
            w.and(q -> q.like(Instance::getName, keyword)
                    .or().like(Instance::getIp, keyword)
                    .or().like(Instance::getInstanceId, keyword));
        }
        w.orderByDesc(Instance::getLastHeartbeatTime);
        return instanceMapper.selectList(w);
    }

    @Override
    public Instance getInstance(String instanceId) {
        Instance ins = instanceMapper.selectOne(
                new LambdaQueryWrapper<Instance>().eq(Instance::getInstanceId, instanceId).last("LIMIT 1"));
        if (ins == null) {
            throw new BizException(ResultCode.INSTANCE_NOT_FOUND);
        }
        return ins;
    }

    @Override
    @Transactional
    public void bind(String instanceId, String remark) {
        Instance exist = getInstance(instanceId);
        LocalDateTime now = LocalDateTime.now();
        Instance upd = new Instance();
        upd.setId(exist.getId());
        upd.setBindStatus("bound");
        upd.setBindTime(now);
        if (remark != null && !remark.isEmpty()) {
            upd.setRemark(remark);
        }
        upd.setUpdateTime(now);
        instanceMapper.updateById(upd);
        saveLog(instanceId, "bind", "管理后台绑定" + (remark == null ? "" : (": " + remark)));
    }

    @Override
    @Transactional
    public void manualDeregister(String instanceId, String reason) {
        Instance exist = getInstance(instanceId);
        LocalDateTime now = LocalDateTime.now();
        Instance upd = new Instance();
        upd.setId(exist.getId());
        upd.setStatus("deregistered");
        upd.setUpdateTime(now);
        instanceMapper.updateById(upd);
        saveLog(instanceId, "deregister", "管理后台手动注销: " + (reason == null ? "" : reason));
    }

    @Override
    public Map<String, Object> statistics() {
        Map<String, Object> result = new HashMap<>();
        result.put("total", instanceMapper.selectCount(null));
        result.put("online", instanceMapper.selectCount(
                new LambdaQueryWrapper<Instance>().eq(Instance::getStatus, "online")));
        result.put("offline", instanceMapper.selectCount(
                new LambdaQueryWrapper<Instance>().eq(Instance::getStatus, "offline")));
        result.put("deregistered", instanceMapper.selectCount(
                new LambdaQueryWrapper<Instance>().eq(Instance::getStatus, "deregistered")));
        result.put("bound", instanceMapper.selectCount(
                new LambdaQueryWrapper<Instance>().eq(Instance::getBindStatus, "bound")));
        result.put("unbound", instanceMapper.selectCount(
                new LambdaQueryWrapper<Instance>().eq(Instance::getBindStatus, "unbound")));
        return result;
    }

    /* ============================================================
     *                       内部方法
     * ============================================================ */

    /**
     * 定时任务调用：把心跳超时的实例标为离线
     */
    @Transactional
    public int markOfflineIfTimeout(LocalDateTime threshold) {
        Instance upd = new Instance();
        upd.setStatus("offline");
        upd.setUpdateTime(LocalDateTime.now());
        int rows = instanceMapper.update(upd,
                new LambdaUpdateWrapper<Instance>()
                        .eq(Instance::getStatus, "online")
                        .lt(Instance::getLastHeartbeatTime, threshold));
        if (rows > 0) {
            log.info("[heartbeat-check] 标记 {} 个实例为离线", rows);
        }
        return rows;
    }

    /**
     * 定时任务调用：长时间离线则自动注销（删除/标记 deregistered）
     */
    @Transactional
    public int autoDeregister(LocalDateTime threshold) {
        // 先查出离线超时的实例，挨个记日志
        List<Instance> list = instanceMapper.selectList(
                new LambdaQueryWrapper<Instance>()
                        .eq(Instance::getStatus, "offline")
                        .lt(Instance::getLastHeartbeatTime, threshold));
        if (list.isEmpty()) return 0;

        Instance upd = new Instance();
        upd.setStatus("deregistered");
        upd.setUpdateTime(LocalDateTime.now());
        int rows = instanceMapper.update(upd,
                new LambdaUpdateWrapper<Instance>()
                        .eq(Instance::getStatus, "offline")
                        .lt(Instance::getLastHeartbeatTime, threshold));
        for (Instance i : list) {
            saveLog(i.getInstanceId(), "auto_deregister", "长时间离线，自动注销");
        }
        log.info("[auto-deregister] 自动注销 {} 个实例", rows);
        return rows;
    }

    private void saveLog(String instanceId, String action, String detail) {
        try {
            InstanceLog l = new InstanceLog();
            l.setInstanceId(instanceId);
            l.setAction(action);
            l.setDetail(detail);
            l.setCreateTime(LocalDateTime.now());
            instanceLogMapper.insert(l);
        } catch (Exception e) {
            log.warn("写操作日志失败: {}", e.getMessage());
        }
    }
}