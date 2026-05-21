package com.jacob.pumasi.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 사진 응답 — id는 신고 시 필요, src는 표시용 */
@Getter
@AllArgsConstructor
public class PhotoView {
	private final Long id;
	private final String src;
}
