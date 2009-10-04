/*
 * Copyright (C) 2003-2004 Micah N. Gorrell
 * ineedsleep@myrealbox.com
 * 172 N 700 W
 * Spanish Fork, UT 84660
 * 
 * LICENSE
 * =======
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of version 2 of the GNU General Public License as
 * published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of       
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License    
 * along with this program; if not, write to the Free Software          
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA. 
 */

package com.frozenbutterfly.keychain;

import danger.app.*;
import danger.ui.*;
import danger.audio.*;
import danger.util.*;
import danger.util.DEBUG;
import java.util.Random;

import net.skdr.security.Security;

public class keychain extends Application implements Resources, Commands {
	private static final boolean	debug = true;

	static LoginWindow				login;
	static KeyList						keys = null;
	static DataStore					storedData;

	public byte[]						pass = null;

	public keychain() {
		storedData = DataStore.createDataStore("keychainV1", true, true);

		//storedData.removeAllRecords();

		login	= new LoginWindow(this);
	}

	public void launch() {
	}

	public void resume() {
		if (debug || Security.hasNotExpired("lgj9kl3ofblgj9kl3ofb", true)) {
			login.show();
			login.setFocusedChild(login.passwordField);
			login.passwordField.clear();
		} else {
			returnToLauncher();
		}
	}

	public void suspend() {
		/*
			We now need to ensure that the LoginWindow is the only window showing, so that no
			important data is at risk.  We also need to ensure that every place a password
			is ever kept in memory is overwritten with random data.
		*/

		if (keys != null) {
			keys.hide();
		}

		pass = null;
	}
}
