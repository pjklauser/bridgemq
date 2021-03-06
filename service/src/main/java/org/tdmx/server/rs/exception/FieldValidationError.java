/*
 * TDMX - Trusted Domain Messaging eXchange
 * 
 * Enterprise B2B messaging between separate corporations via interoperable cloud service providers.
 * 
 * Copyright (C) 2014 Peter Klauser (http://tdmx.org)
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Affero General Public License along with this program. If not, see
 * http://www.gnu.org/licenses/.
 */
package org.tdmx.server.rs.exception;

public class FieldValidationError {

	public enum FieldValidationErrorType {
		PRESENT,
		MISSING,
		TOO_LONG,
		INVALID,
		EXISTS,
		NOT_EXISTS,
		IMMUTABLE,;
	}

	private final String fieldName;
	private final FieldValidationErrorType type;

	public FieldValidationError(FieldValidationErrorType type, String fieldName) {
		this.type = type;
		this.fieldName = fieldName;
	}

	public String getMessage() {
		return type + " " + fieldName;
	}

}
