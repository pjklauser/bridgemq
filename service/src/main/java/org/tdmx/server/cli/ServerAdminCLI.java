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
package org.tdmx.server.cli;

import java.io.InputStreamReader;

import org.springframework.beans.factory.access.BeanFactoryLocator;
import org.springframework.beans.factory.access.BeanFactoryReference;
import org.springframework.context.ApplicationContext;
import org.springframework.context.access.ContextSingletonBeanFactoryLocator;
import org.tdmx.core.cli.CliParser;
import org.tdmx.core.cli.InputStreamTokenizer;

public class ServerAdminCLI {

	// -------------------------------------------------------------------------
	// PUBLIC CONSTANTS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PROTECTED AND PRIVATE VARIABLES AND CONSTANTS
	// -------------------------------------------------------------------------
	private static ApplicationContext context;

	private ServerAdminCLI() {
	}

	public static void main(String[] args) {
		BeanFactoryLocator beanFactoryLocator = ContextSingletonBeanFactoryLocator.getInstance();
		BeanFactoryReference beanFactoryReference = beanFactoryLocator.useBeanFactory("adminContext");
		context = (ApplicationContext) beanFactoryReference.getFactory();

		CliParser clirunner = (CliParser) context.getBean("tdmx.server.cli.ServerAdminCLI");

		InputStreamTokenizer tokenizer = args.length > 0 ? new InputStreamTokenizer(args)
				: new InputStreamTokenizer(new InputStreamReader(System.in));

		clirunner.process(tokenizer, System.out, System.err);

	}

	// -------------------------------------------------------------------------
	// CONSTRUCTORS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PUBLIC METHODS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PROTECTED METHODS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PRIVATE METHODS
	// -------------------------------------------------------------------------

	// -------------------------------------------------------------------------
	// PUBLIC ACCESSORS (GETTERS / SETTERS)
	// -------------------------------------------------------------------------

}
