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
package org.tdmx.server.ros;

/**
 * Executes a job.
 * 
 * @author Peter
 *
 */
public interface RelayJobExecutionService {

	/**
	 * Set the segment's SCS URL to allow detection of own segment relay shortcut.
	 * 
	 * @param segmentScsUrl
	 */
	public void setSegmentScsUrl(String segmentScsUrl);

	/**
	 * Execute the job.
	 * 
	 * Results of job are stored in the job.
	 * 
	 * @param job
	 */
	public void executeJob(RelayJobContext job);

}
