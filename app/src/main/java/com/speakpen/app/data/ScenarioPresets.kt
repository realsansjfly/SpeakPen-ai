package com.speakpen.app.data

/** Original app-authored templates, not imported third-party curricula or licensed word lists. */
object ScenarioPresets {
    val all: List<ScenarioTemplate> = listOf(
        preset("cafe", "咖啡店点餐", "咖啡店店员", "顾客", listOf("点一杯想要的饮品", "说明甜度或牛奶偏好", "确认订单并询问总价"),
            "Hi! What would you like to drink today?"),
        preset("shopping", "商店购物", "服装店店员", "顾客", listOf("说明想买的衣物", "询问尺码或颜色", "询问价格并明确购买决定"),
            "Hello! What are you looking for today?"),
        preset("directions", "出行问路", "熟悉附近的路人", "需要问路的旅行者", listOf("说出想去的地点", "询问路线或交通方式", "复述关键路线并确认"),
            "Hi! Do you need help finding somewhere?"),
        preset("hotel", "酒店入住", "酒店前台工作人员", "已预订房间的客人", listOf("说明预订姓名及入住需求", "询问早餐或退房时间", "确认入住安排"),
            "Welcome! Do you have a reservation?"),
        preset("campus", "校园社交", "初次见面的同学", "新同学", listOf("介绍自己的姓名和学习方向", "询问对方的兴趣", "提出一起参加活动的邀请并确认安排"),
            "Hi! I don't think we've met. I'm Alex. What's your name?"),
        preset("interview", "工作面试", "面试官", "求职者", listOf("介绍自己的相关背景", "用一个具体例子说明能力", "询问岗位问题并确认交流已结束"),
            "Thanks for joining us. Could you tell me a little about your background?"),
    )
    private fun preset(id: String, name: String, aiRole: String, userRole: String, goals: List<String>, opening: String) = ScenarioTemplate(
        id = "builtin:$id", name = name, aiRole = aiRole, userRole = userRole, opening = opening,
        goals = goals.mapIndexed { index, text -> ScenarioGoal("$id:${index + 1}", text) },
        endCondition = "用户在实际对话中完成以下全部目标后结束：" + goals.joinToString("；"), maxTurns = 12,
    )
}
