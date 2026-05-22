package jp.co.sss.lms.form;

import java.util.LinkedHashMap;
import java.util.List;

import jakarta.validation.Valid;
import lombok.Data;

/**
 * 勤怠フォーム
 * 
 * @author 東京ITスクール
 */
@Data
@Valid
public class AttendanceForm {

	/** LMSユーザーID */
	private Integer lmsUserId;
	/** グループID */
	private Integer groupId;
	/** 年間計画No */
	private String nenkanKeikakuNo;
	/** ユーザー名 */
	private String userName;
	/** 退校フラグ */
	private Integer leaveFlg;
	/** 退校日 */
	private String leaveDate;
	/** 退校日（表示用） */
	private String dispLeaveDate;
	/** 中抜け時間(プルダウン) */
	private LinkedHashMap<Integer, String> blankTimes;
	/** 日次の勤怠フォームリスト */
	@Valid
	private List<DailyAttendanceForm> attendanceList;
	
	/** 里行哉 - Task26 */
	/** 出勤時間時（プルダウン) */
	private LinkedHashMap<Integer, String> trainingStartHours;
	/** 出勤時間分（プルダウン）*/
	private LinkedHashMap<Integer, String> trainingStartMinutes;
	/** 退勤時間時（プルダウン）*/
	private LinkedHashMap<Integer, String> trainingEndHours;
	/** 退勤時間分（プルダウン）*/
	private LinkedHashMap<Integer, String> trainingEndMinutes;
	
}
