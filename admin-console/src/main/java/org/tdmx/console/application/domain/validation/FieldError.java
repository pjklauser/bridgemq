package org.tdmx.console.application.domain.validation;

import org.tdmx.console.application.domain.DomainObjectField;




/**
 * 
 * @author Peter
 *
 */
public class FieldError {

	//-------------------------------------------------------------------------
	//PUBLIC CONSTANTS
	//-------------------------------------------------------------------------
	public static enum ERROR {
		MISSING, PRESENT, INVALID,
	}
	
	
	//-------------------------------------------------------------------------
	//PROTECTED AND PRIVATE VARIABLES AND CONSTANTS
	//-------------------------------------------------------------------------
	private DomainObjectField field;
	private int position = 0;
	private ERROR error;
	
	//-------------------------------------------------------------------------
	//CONSTRUCTORS
	//-------------------------------------------------------------------------
	
	public FieldError(DomainObjectField field, ERROR errorCode) {
		this.field = field;
		this.error = errorCode;
	}
	
	public FieldError(DomainObjectField field, int pos, ERROR errorCode) {
		this.field = field;
		this.position = pos;
		this.error = errorCode;
	}
	
	//-------------------------------------------------------------------------
	//PUBLIC METHODS
	//-------------------------------------------------------------------------
	
	@Override
	public String toString() {
		if ( position > 0 ) {
			return getError()+"["+getField()+"("+getPosition()+")]";
		} else {
			return getError()+"["+getField()+"]";
		}
	}
	
    //-------------------------------------------------------------------------
	//PROTECTED METHODS
	//-------------------------------------------------------------------------

	//-------------------------------------------------------------------------
	//PRIVATE METHODS
	//-------------------------------------------------------------------------

	//-------------------------------------------------------------------------
	//PUBLIC ACCESSORS (GETTERS / SETTERS)
	//-------------------------------------------------------------------------

	public DomainObjectField getField() {
		return field;
	}

	public int getPosition() {
		return position;
	}

	public ERROR getError() {
		return error;
	}

}
