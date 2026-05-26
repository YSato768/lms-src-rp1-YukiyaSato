package jp.co.sss.lms.service;

import java.text.ParseException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.annotation.Validated;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
@Validated
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		/**
		 * @author 里行哉 - Task26
		 * AttendanceFormにログインユーザー情報や選択用マップ（時・分）を設定する。
		 */
		attendanceForm.setTrainingStartHours(attendanceUtil.getHourMap());
		attendanceForm.setTrainingStartMinutes(attendanceUtil.getMinuteMap());
		attendanceForm.setTrainingEndHours(attendanceUtil.getHourMap());
		attendanceForm.setTrainingEndMinutes(attendanceUtil.getMinuteMap());

		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));

			/** 里行哉 - Task26 */
			//出勤時刻、退勤時刻から時と分を抽出
			if (!(attendanceManagementDto.getTrainingStartTime() == null)) {
				if (!(attendanceManagementDto.getTrainingStartTime().equals(""))) {
					dailyAttendanceForm.setStartHour(
							attendanceUtil.calcTrainingTimeHour(attendanceManagementDto.getTrainingStartTime()));
					dailyAttendanceForm.setStartMinutes(
							attendanceUtil.calcTrainingTimeMinutes(attendanceManagementDto.getTrainingStartTime()));
				}
			}
			if (!(attendanceManagementDto.getTrainingEndTime() == null)) {
				if (!(attendanceManagementDto.getTrainingEndTime().equals(""))) {
					dailyAttendanceForm.setEndHour(
							attendanceUtil.calcTrainingTimeHour(attendanceManagementDto.getTrainingEndTime()));
					dailyAttendanceForm.setEndMinutes(
							attendanceUtil.calcTrainingTimeMinutes(attendanceManagementDto.getTrainingEndTime()));
				}
			}

			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		/** 里行哉 - Task26 */
		//プルダウン形式の時刻からhh:mmの形式へ変換
		formatConversion(attendanceForm);
		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 今日より前の過去日に、未入力の勤怠があるかどうかを判定する
	 * 
	 * @author 里行哉 - Task25
	 * @return 勤怠未入力件数が1件でもあった場合trueを返す
	 * @return 勤怠未入力件数が0件の場合falseを返す
	 * @throws ParseException
	 */
	public boolean notEnterCheck() throws ParseException {
		//現在の日付を取得
		Date date = new Date();
		//yyyy/DD/ddの形式へ変換
		String stringDate = dateUtil.toString(date);
		Date formatDate = dateUtil.parse(stringDate);
		//ログインユーザー情報を取得
		LoginUserDto loginUserDto = loginUserUtil.getLoginUserDto();
		if (tStudentAttendanceMapper.notEnterCount(loginUserDto.getLmsUserId(),
				(short) Constants.DB_FLG_FALSE, formatDate) > 0) {
			//勤怠未入力件数が1件でもあった場合trueを返す
			return true;

		} else {
			//勤怠未入力件数が0件の場合falseを返す
			return false;

		}
	}

	/**
	 * プルダウン形式の時刻からhh:mmの形式へ変換
	 * 
	 * @author 里行哉 - Task26
	 * @param attendanceForm 勤怠情報リスト
	 */
	public void formatConversion(AttendanceForm attendanceForm) {

		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			if (!(dailyAttendanceForm.getStartHour().equals(""))
					&& !(dailyAttendanceForm.getStartMinutes().equals(""))) {
				String timeString = String.format("%02d:%02d", Integer.parseInt(dailyAttendanceForm.getStartHour()),
						Integer.parseInt(dailyAttendanceForm.getStartMinutes()));
				dailyAttendanceForm.setTrainingStartTime(timeString);
			}

			if (!(dailyAttendanceForm.getEndHour().equals(""))
					&& !(dailyAttendanceForm.getEndMinutes().equals(""))) {
				String timeString = String.format("%02d:%02d", Integer.parseInt(dailyAttendanceForm.getEndHour()),
						Integer.parseInt(dailyAttendanceForm.getEndMinutes()));
				dailyAttendanceForm.setTrainingEndTime(timeString);
			}
		}
	}
	
	/**
	 * 勤怠情報入力チェック
	 * 
	 * @author 里行哉 - Task27
	 * @param attendanceForm 入力されたフォーム
	 * @param result 入力エラー格納
	 * @throws ParseException 
	 */
	public void updateInputCheck(AttendanceForm attendanceForm, BindingResult result) throws ParseException {

		//リストの要素数取得用変数
		Integer indexNum = 0;
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {
			//備考が文字数100を超えていた場合エラー情報を追加
			if (dailyAttendanceForm.getNote().length() > 100) {
				String note = messageUtil.getMessage("note");
				String max = "100";
				result.addError(new FieldError(result.getObjectName(), "attendanceList[" + indexNum + "].note",
						messageUtil.getMessage("maxlength", new String[] { note, max })));
			}
			//出勤時間の時、分いずれかが未入力の場合エラー情報を追加
			if (!(dailyAttendanceForm.getStartHour() == "" && dailyAttendanceForm.getStartMinutes() == "")) {
				String inputTrainingStartTime = "出勤時間";
				if (dailyAttendanceForm.getStartHour() == "") {
					result.addError(new FieldError(result.getObjectName(), "attendanceList[" + indexNum + "].startHour",
							messageUtil.getMessage("input.invalid", new String[] { inputTrainingStartTime })));
				}
				if (dailyAttendanceForm.getStartMinutes() == "") {
					result.addError(new FieldError(result.getObjectName(), "attendanceList[" + indexNum + "].startMinutes",
							messageUtil.getMessage("input.invalid", new String[] { inputTrainingStartTime })));
				}
			}
			//退勤時間の時、分いずれかが未入力の場合エラー情報を追加
			if (!(dailyAttendanceForm.getEndHour() == "" && dailyAttendanceForm.getEndMinutes() == "")) {
				String inputTrainingEndTime = "退勤時間";
				if (dailyAttendanceForm.getEndHour() == "") {
					result.addError(new FieldError(result.getObjectName(), "attendanceList[" + indexNum + "].endHour",
							messageUtil.getMessage("input.invalid", new String[] { inputTrainingEndTime })));
				}
				if (dailyAttendanceForm.getEndMinutes() == "") {
					result.addError(new FieldError(result.getObjectName(), "attendanceList[" + indexNum + "].endMinutes",
							messageUtil.getMessage("input.invalid", new String[] { inputTrainingEndTime })));
				}
			}
			//出勤時間が未入力の状態で退勤時間を記入した場合エラー情報を追加
			if (dailyAttendanceForm.getStartHour() == "" || dailyAttendanceForm.getStartMinutes() == "") {
				if (dailyAttendanceForm.getEndHour() != "" && dailyAttendanceForm.getEndMinutes() != "") {
					result.addError(new FieldError(result.getObjectName(), "attendanceList[" + indexNum + "].startHour",
							messageUtil.getMessage("attendance.punchInEmpty")));
					result.addError(new FieldError(result.getObjectName(), "attendanceList[" + indexNum + "].startMinutes",
							messageUtil.getMessage("attendance.punchInEmpty")));
				}
			}
			//退勤時間が出勤時間よりも前の場合エラー情報を追加
			if (dailyAttendanceForm.getStartHour() != ""
					&& dailyAttendanceForm.getStartMinutes() != ""
					&& dailyAttendanceForm.getEndHour() != ""
					&& dailyAttendanceForm.getEndMinutes() != "") {
				LocalTime startTime = LocalTime.of(Integer.parseInt(dailyAttendanceForm.getStartHour()), Integer.parseInt(dailyAttendanceForm.getStartMinutes()));
				LocalTime endTime = LocalTime.of(Integer.parseInt(dailyAttendanceForm.getEndHour()), Integer.parseInt(dailyAttendanceForm.getEndMinutes()));
				if (!startTime.isBefore(endTime)){
					result.addError(new FieldError(result.getObjectName(), "attendanceList[" + indexNum + "].trainingStartTime",
							messageUtil.getMessage("attendance.trainingTimeRange",
									new String[] { String.valueOf(indexNum) })));
				}
			}
			//中抜け時間が勤務時間を超えた場合エラー情報を追加
			if (dailyAttendanceForm.getStartHour() != ""
					&& dailyAttendanceForm.getStartMinutes() != ""
					&& dailyAttendanceForm.getEndHour() != ""
					&& dailyAttendanceForm.getEndMinutes() != "") {
				Integer differenceHour = Integer.parseInt(dailyAttendanceForm.getEndHour()) - Integer.parseInt(dailyAttendanceForm.getStartHour());
				Integer differenceMinutes = Integer.parseInt(dailyAttendanceForm.getEndMinutes()) - Integer.parseInt(dailyAttendanceForm.getStartMinutes());
				Integer totalMinutes = differenceHour * 60 + differenceMinutes;
				if (dailyAttendanceForm.getBlankTime() != null) {
					if (dailyAttendanceForm.getBlankTime() > totalMinutes) {
						result.addError(new FieldError(result.getObjectName(), "attendanceList[" + indexNum + "].blankTime",
								messageUtil.getMessage("attendance.blankTimeError")));
					}
				}
			}
			dailyAttendanceForm.setIndex(String.valueOf(indexNum));
			indexNum++;
			
		}
	}

}
