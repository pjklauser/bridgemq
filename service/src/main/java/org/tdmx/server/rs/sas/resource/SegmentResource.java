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
package org.tdmx.server.rs.sas.resource;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import org.tdmx.core.cli.display.annotation.CliAttribute;
import org.tdmx.core.cli.display.annotation.CliRepresentation;
import org.tdmx.lib.control.domain.Segment;

@CliRepresentation(name = "Segment")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "segment")
@XmlType(name = "Segment")
public class SegmentResource {

	public enum FIELD {
		ID("id"),
		SEGMENT("segment"),
		SCS_URL("scsUrl"),;

		private final String n;

		private FIELD(String n) {
			this.n = n;
		}

		@Override
		public String toString() {
			return this.n;
		}
	}

	@CliAttribute(order = 0, verbose = true)
	private Long id;
	@CliAttribute(order = 1)
	private String segment;
	@CliAttribute(order = 2)
	private String scsUrl;

	public static Segment mapTo(SegmentResource segment) {
		if (segment == null) {
			return null;
		}
		Segment s = new Segment();
		s.setId(segment.getId());
		s.setSegmentName(segment.getSegment());

		s.setScsUrl(segment.getScsUrl());
		return s;
	}

	public static SegmentResource mapFrom(Segment segment) {
		if (segment == null) {
			return null;
		}
		SegmentResource a = new SegmentResource();
		a.setId(segment.getId());
		a.setSegment(segment.getSegmentName());

		a.setScsUrl(segment.getScsUrl());
		return a;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getSegment() {
		return segment;
	}

	public void setSegment(String segment) {
		this.segment = segment;
	}

	public String getScsUrl() {
		return scsUrl;
	}

	public void setScsUrl(String scsUrl) {
		this.scsUrl = scsUrl;
	}

}
