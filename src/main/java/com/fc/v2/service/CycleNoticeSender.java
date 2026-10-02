package com.fc.v2.service;

/**
 * 催办两路送达的出站口：物业走站内消息，业委会联系人走手机短信。
 * 两路各自送、各自回报是否送达，谁也顶不了谁；服务层按路单独记一笔。
 *
 * @author fuce
 * @date 2026-10-02
 */
public interface CycleNoticeSender {

    /** 物业那一路：站内消息。 */
    boolean sendStationMessage(String siteNo, String text);

    /** 业委会联系人那一路：手机短信。 */
    boolean sendSms(String mobile, String text);
}
