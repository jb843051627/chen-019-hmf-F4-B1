-- hmf 住宅专项维修资金交存与使用监督管理 -- schema (chen-019)
-- 列名与基线实体契约（@TableName/@TableField）逐列对齐，改列必须同步实体。
-- 库：chen_019

CREATE TABLE IF NOT EXISTS t_hmf_acct_card (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '分户账立户单号',
  node_no int DEFAULT NULL COMMENT '本单版次序',
  site_id int DEFAULT NULL COMMENT '所属分户底册',
  site_no varchar(64) DEFAULT NULL COMMENT '所属底册代号',
  qty decimal(12,2) DEFAULT NULL COMMENT '本户建筑面积(平方米)',
  fine_amt decimal(12,2) DEFAULT NULL COMMENT '本户应缴额(元)',
  content varchar(255) DEFAULT NULL COMMENT '校验串（随版次序走）',
  grade_level int DEFAULT NULL COMMENT '面积折到的收费档次',
  status int DEFAULT NULL COMMENT '进展 0待核对 1已核对 2已冻住',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分户账立户单';

CREATE TABLE IF NOT EXISTS t_hmf_arch (
  id bigint NOT NULL COMMENT '主键',
  site_no varchar(64) DEFAULT NULL COMMENT '分户底册代号',
  site_name varchar(128) DEFAULT NULL COMMENT '小区（项目）全称',
  site_type varchar(32) DEFAULT NULL COMMENT '项目类别',
  road_name varchar(128) DEFAULT NULL COMMENT '落在哪一级辖区(市—区—街道)',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '户建面低界(平方米)',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '中界(平方米)',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '高界(平方米)',
  status int DEFAULT NULL COMMENT '底册情形 0在册 1已迁出',
  contact_mobile varchar(32) DEFAULT NULL COMMENT '业委会联系人手机号（催交走短信那一路）',
  contact_fill_at datetime DEFAULT NULL COMMENT '联系人栏最近填报时刻（一个季度没填过即当失联）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分户底册';

CREATE TABLE IF NOT EXISTS t_hmf_budget_bill (
  id bigint NOT NULL COMMENT '主键',
  bill_no varchar(64) DEFAULT NULL COMMENT '预算审定拨付单号',
  node_no int DEFAULT NULL COMMENT '当前所在口次 0..2（业委会核/事务所核/中心核）',
  sign_mode int DEFAULT NULL COMMENT '本口签法镜像 0任一人 1两名点齐（算法按口次定死，此列只照当前口写出）',
  need_count int DEFAULT NULL COMMENT '本口应落名数（当前口镜像，业委会2/事务所1/中心1）',
  sign_count int DEFAULT NULL COMMENT '本口已落名数（只按署名流水逐笔勾出，不以人手记数为准）',
  cur_round int DEFAULT '1' COMMENT '当前口签认拨次（打回上一档时另起一拨，旧拨留存）',
  status int DEFAULT NULL COMMENT '报批情形 0在核 1已核讫 2已打回',
  maker varchar(64) DEFAULT NULL COMMENT '立单经手人（起单到归档不换，重录亦不改）',
  pay_amt decimal(12,2) DEFAULT NULL COMMENT '拨付额（立单时一次取自预算审定额，库存与屏显同源，不许后手填改）',
  site_no varchar(64) DEFAULT NULL COMMENT '所属小区底册代号（同年另起新单，不并入旧单）',
  bill_year int DEFAULT NULL COMMENT '本单所属年度',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者（实际操作人，随事务续名）',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  UNIQUE KEY uk_bill_no (bill_no),
  KEY idx_site_year (site_no, bill_year),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预算审定拨付核签单';

-- 署名流水：一笔一行、只添不删不抹。新旧两拨（打回前/补签）以 round_no 分存，各自留名留时。
CREATE TABLE IF NOT EXISTS t_hmf_budget_sign (
  id bigint NOT NULL COMMENT '主键',
  bill_id bigint NOT NULL COMMENT '所属预算审定拨付核签单',
  node_no int DEFAULT NULL COMMENT '落名口次 0业委会 1事务所 2中心',
  round_no int DEFAULT NULL COMMENT '该口第几拨签名（打回重走添新拨，旧拨保留）',
  signer varchar(64) DEFAULT NULL COMMENT '实际署名（操作）人，逐笔实记，不许笼统记成固定一人',
  action int DEFAULT '0' COMMENT '本笔动作 0签认 1打回',
  sign_time datetime DEFAULT NULL COMMENT '落笔时刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（打回事由等）',
  KEY idx_bill (bill_id, node_no, round_no),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='预算审定拨付核签署名流水';

CREATE TABLE IF NOT EXISTS t_hmf_cycle_task (
  id bigint NOT NULL COMMENT '主键',
  item_no varchar(64) DEFAULT NULL COMMENT '结息催交单号',
  site_no varchar(64) DEFAULT NULL COMMENT '对哪一户分户底册（按底册代号挂）',
  biz_type int DEFAULT NULL COMMENT '事由 1结息 2催交',
  due_at datetime DEFAULT NULL COMMENT '应该出手那一天的止点时刻（钉死，不许为凑批后挪）',
  amount decimal(12,2) DEFAULT NULL COMMENT '可提前几日开口（提前期，天数）',
  tpl_code varchar(64) DEFAULT NULL COMMENT '按哪一份事由文本去办',
  content varchar(255) DEFAULT NULL COMMENT '事由与被催分户底册记要',
  status int DEFAULT NULL COMMENT '条目情形 0候办 1已办结 2催不动',
  finish_at datetime DEFAULT NULL COMMENT '完成那一刻（办结两笔之一，与情形翻转同脚落，缺一笔不算完）',
  fail_at datetime DEFAULT NULL COMMENT '判催不动那一刻（撞墙/逾期撤回）',
  fail_reason varchar(500) DEFAULT NULL COMMENT '为什么催不动（迁出/失联/逾期撤回，跟着记进行里）',
  last_round_at datetime DEFAULT NULL COMMENT '最近一次进轮次的扫描时刻',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  KEY idx_site (site_no),
  KEY idx_due (due_at),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结息催交单';

-- 轮次出手痕迹：定时任务每轮一条结局/送达行，只添。同一次 runOnce 的所有行 round_at 相同，
-- 页面三数照 round_at 从本表逐条点出（等着办=按住/封存撞车，已办结=办结，催不动=判死/撤回）。
-- 催交两路送达各立一行（channel 1站内 2短信），谁也顶不了谁。
CREATE TABLE IF NOT EXISTS t_hmf_cycle_log (
  id bigint NOT NULL COMMENT '主键',
  task_id bigint NOT NULL COMMENT '所属结息催交单',
  site_no varchar(64) DEFAULT NULL COMMENT '所属分户底册代号',
  biz_type int DEFAULT NULL COMMENT '事由 1结息 2催交',
  round_at datetime DEFAULT NULL COMMENT '本轮扫描时刻（同一次执行的行取同一值）',
  action int DEFAULT NULL COMMENT '本笔动作 1办结 2撞墙判催不动 3逾期撤回转催不动 4封存撞车拒收 5窗口压着候办 10站内送达 11短信送达',
  status_after int DEFAULT NULL COMMENT '本笔落定后条目情形（结局行用）',
  channel int DEFAULT NULL COMMENT '送达路 1物业站内消息 2业委会联系人手机短信（仅送达行）',
  delivered int DEFAULT NULL COMMENT '该路是否送达 1送达 0未送达（仅送达行）',
  log_time datetime DEFAULT NULL COMMENT '落笔时刻（即落库时刻）',
  detail varchar(500) DEFAULT NULL COMMENT '明细（撞墙缘由、撤回凭据、撞车说明等）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  KEY idx_round (round_at),
  KEY idx_task (task_id),
  KEY idx_site_day (site_no, log_time),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='结息催交单轮次出手痕迹';

CREATE TABLE IF NOT EXISTS t_hmf_pay_book (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '银行交存汇总册次号（进门定死，在册唯一）',
  total_rows int DEFAULT NULL COMMENT '本册行数（册首数，与逐行点出的同回写出）',
  cleared_rows int DEFAULT NULL COMMENT '已销行数（册首数）',
  held_rows int DEFAULT NULL COMMENT '挂起行数（册首数）',
  result int DEFAULT NULL COMMENT '核销结果 1核讫 2空册（全册无一行能核只许写空册）',
  status int DEFAULT '0' COMMENT '册情形 0在册 1已撤（要改先走撤册手续）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者（撤册经办）',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（撤册事由等）',
  KEY idx_batch_status (batch_no, status),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='银行交存汇总册册头';

CREATE TABLE IF NOT EXISTS t_hmf_pay_row (
  id bigint NOT NULL COMMENT '主键',
  batch_no varchar(64) DEFAULT NULL COMMENT '银行交存汇总册次号',
  book_id bigint DEFAULT NULL COMMENT '所属在册册头（撤册重进门另起册头，新旧行不混）',
  row_no int DEFAULT NULL COMMENT '册内行次（进册按先后自占，挂起行也带原来排第几）',
  item_code varchar(64) DEFAULT NULL COMMENT '被对上的分户账户号（销账只认户号）',
  qty decimal(12,2) DEFAULT NULL COMMENT '本行到帐金额',
  pay_date date DEFAULT NULL COMMENT '交存日期（落在哪季算哪季，季末含当日算本期）',
  from_season varchar(8) DEFAULT NULL COMMENT '所属对账季（进门按交存日期钉死）',
  status int DEFAULT NULL COMMENT '行落地情形 0待销 1已销账 2挂起',
  layer int DEFAULT '0' COMMENT '所在层 0现行册面 1往期（挂起连跨两季挪此层，只供翻）',
  hold_reason varchar(500) DEFAULT NULL COMMENT '挂起缘由（户号对不上/累计超应缴，就地写明）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  KEY idx_book (book_id, layer, row_no),
  KEY idx_batch (batch_no),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='银行交存汇总册核销行';

CREATE TABLE IF NOT EXISTS t_hmf_spend_line (
  id bigint NOT NULL COMMENT '主键',
  rule_code varchar(64) DEFAULT NULL COMMENT '支出审定额线代号',
  rule_name varchar(128) DEFAULT NULL COMMENT '线名',
  th1_max decimal(12,2) DEFAULT NULL COMMENT '低界金额',
  th2_max decimal(12,2) DEFAULT NULL COMMENT '中界金额',
  th3_max decimal(12,2) DEFAULT NULL COMMENT '高界金额',
  eff_start datetime DEFAULT NULL COMMENT '启用之日',
  eff_end datetime DEFAULT NULL COMMENT '交棒之日(不含)',
  priority int DEFAULT NULL COMMENT '让线顺位(数值大的先说话)',
  status int DEFAULT NULL COMMENT '线的情形 0现行 1已停用',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='支出审定额线';

CREATE TABLE IF NOT EXISTS t_hmf_use_flow (
  id bigint NOT NULL COMMENT '主键',
  biz_no varchar(64) DEFAULT NULL COMMENT '使用申请单号',
  site_no varchar(64) DEFAULT NULL COMMENT '所属小区底册代号（户面余额按底册点）',
  stage int DEFAULT NULL COMMENT '当前段次 0..3（立项/公示/施工/结算）；仅镜像，权威段次由过口痕迹重放得出',
  status int DEFAULT NULL COMMENT '申领会落 0未起 1在办 2已作废 3已办结',
  cur_round int DEFAULT '1' COMMENT '当前拨次（退回上一档旧拨沉底，重走另起一拨）',
  project_place varchar(255) DEFAULT NULL COMMENT '门槛·工程地点（立项四样之一）',
  budget_amt decimal(12,2) DEFAULT NULL COMMENT '门槛·预算金额（立项四样之一；列支照此数划走）',
  builder_name varchar(128) DEFAULT NULL COMMENT '门槛·施工单位名称（立项四样之一）',
  apply_source varchar(64) DEFAULT NULL COMMENT '门槛·申请来源（物业报的/业委会报的）',
  public_days int DEFAULT NULL COMMENT '门槛·公示天数（进公示档落定）',
  public_start datetime DEFAULT NULL COMMENT '公示起算时刻（本拨进公示档落笔，重走重起算）',
  contract_no varchar(64) DEFAULT NULL COMMENT '门槛·合同要件（施工档挂齐）',
  accept_record varchar(255) DEFAULT NULL COMMENT '门槛·验收记载（结算档要看）',
  supervisor varchar(64) DEFAULT NULL COMMENT '门槛·监理那一名（结算档要到）',
  pay_amt decimal(12,2) DEFAULT NULL COMMENT '已列支金额（结算过门划走）',
  pay_time datetime DEFAULT NULL COMMENT '列支落地时刻',
  content varchar(2000) DEFAULT NULL COMMENT '经办记事',
  last_action varchar(64) DEFAULT NULL COMMENT '最近一次过口动作',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注',
  KEY idx_biz_no (biz_no),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='维修资金使用申请单';

-- 过口痕迹：一次过口一行，只添不删不抹。当前段次照本表时间序重放得出，不以纸面段次为准。
-- 退回收档以 round_no 另起一拨，旧拨行沉底留存；列支、作废各立其行；倒查照本表从结算倒回立项捋。
CREATE TABLE IF NOT EXISTS t_hmf_use_stage_log (
  id bigint NOT NULL COMMENT '主键',
  flow_id bigint NOT NULL COMMENT '所属使用申请单',
  stage_no int DEFAULT NULL COMMENT '落在哪一档 0立项 1公示 2施工 3结算（退回行记从哪档退）',
  round_no int DEFAULT NULL COMMENT '该档第几拨（退回重走添新拨，旧拨沉底）',
  action int DEFAULT '0' COMMENT '本笔动作 0过口 1退回 2列支 3作废',
  operator varchar(64) DEFAULT NULL COMMENT '实际经办（操作）人，逐笔实记',
  log_time datetime DEFAULT NULL COMMENT '落笔时刻（公示照算、事后补录照查的凭据）',
  pay_amt decimal(12,2) DEFAULT NULL COMMENT '列支划走金额（仅 action=2 有值）',
  del_flag int DEFAULT '0' COMMENT '删除标记 0正常 1删除',
  create_by varchar(64) DEFAULT NULL COMMENT '创建者',
  create_time datetime DEFAULT NULL COMMENT '创建时间（即落库时刻，与 log_time 岔开过大即事后补录）',
  update_by varchar(64) DEFAULT NULL COMMENT '更新者',
  update_time datetime DEFAULT NULL COMMENT '更新时间',
  remark varchar(500) DEFAULT NULL COMMENT '备注（门槛材料、退回事由等）',
  KEY idx_flow (flow_id, stage_no, round_no),
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='维修资金使用申请单过口痕迹';

-- 初始档案数据（首条为启用态、第二条为停用态）
-- t_hmf_arch 两条种子底册：id=0 在册、id=1 已迁出（用于挂接与拦截的联动核对）。
-- 单户建面三线上限取 90/144/300 平方米（等于上限的情况计入上一层）。
INSERT IGNORE INTO t_hmf_arch (id, site_no, site_name, site_type, road_name, th1_max, th2_max, th3_max, status, del_flag, create_by, create_time)
VALUES (0, 'JZ00', '澜川市分户底册（梧桐里苑小区，在册）', '商品房', '澜川市—河湾区—长街上', 90.00, 144.00, 300.00, 0, 0, 'seed', NOW()),
       (1, 'JZ01', '澜川市分户底册（滨河北里项目，已迁出）', '保障性住房', '澜川市—河湾区—滨河北', 90.00, 144.00, 300.00, 1, 0, 'seed', NOW());

