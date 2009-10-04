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
import danger.util.*;
import danger.util.DEBUG;
import danger.crypto.*;
import java.util.Random;

public class LoginWindow extends ScreenWindow implements Resources, Commands
{
	keychain					app;
	DataStore				passwordData		= null;

	StaticText				descriptionText	= null;
	StaticText				repeatText			= null;
	PasswordTextField		passwordField		= null;
	PasswordTextField		repeatField			= null;
	Button					okButton				= null;
	Button					cancelButton		= null;
	Rect						view					= null;

	public final int		PASSWORD_ENTRY		= 1;
	public final int		PASSWORD_CANCEL	= 2;

	public static final String			PASSWORD_TEST_DATA	= "This is the password test string.";

	public LoginWindow(keychain app)
	{
		passwordData = DataStore.createDataStore("passwordData", true, true);
		//passwordData.removeAllRecords();

		this.app = app;

		view = getBounds();

		/*
			Once we have the password, and have verified they typed it right, we will md5 it,
			remove the original from memory, and then encrypt a static string.  That string
			will be stored in the dataStore as its own entry, to be used to ensure that the
			password is correct.
		*/

		/* Description Text */
		descriptionText = new StaticText("Enter your password:");
		descriptionText.setTop(20);

		addChild(descriptionText);
		descriptionText.show();

		/* Password field */
		//passwordField = new PasswordTextField();
		passwordField = new PassField(this);
		passwordField.setWidth(200);
		Layout.positionBelow(passwordField, descriptionText, 2);
		passwordField.setLeft((view.getWidth() - passwordField.getWidth()) / 2);

		Layout.alignLeft(descriptionText, passwordField);

		addChild(passwordField);
		passwordField.show();

		/* Second password field, if needed */
		if (passwordData.getRecordCount() == 0) {
			repeatText = new StaticText("Repeat your password:");
			Layout.positionBelow(repeatText, passwordField, 5);

			addChild(repeatText);
			repeatText.show();

			repeatField = new PasswordTextField();
			repeatField.setWidth(200);
			Layout.positionBelow(repeatField, repeatText, 2);
			repeatField.setLeft((view.getWidth() - repeatField.getWidth()) / 2);

			Layout.alignLeft(repeatText, repeatField);

			addChild(repeatField);
			repeatField.show();
		}

		/* Ok and cancel buttons */
		okButton = new Button("OK");
		cancelButton = new Button("Cancel");

		if (repeatField != null) {
			Layout.positionBelow(okButton, repeatField, 5);
			Layout.positionBelow(cancelButton, repeatField, 5);
		} else {
			Layout.positionBelow(okButton, passwordField, 5);
			Layout.positionBelow(cancelButton, passwordField, 5);
		}

		okButton.setEvent(new Event(this, PASSWORD_ENTRY));
		cancelButton.setEvent(new Event(this, PASSWORD_CANCEL));

		okButton.setWidth(60);
		cancelButton.setWidth(60);

		okButton.setLeft((view.getWidth() - (okButton.getWidth() + cancelButton.getWidth() + 5)) / 2);
		cancelButton.setLeft(okButton.getLeft() + 5 + cancelButton.getWidth());

		addChild(okButton);
		addChild(cancelButton);
		okButton.show();
		cancelButton.show();

		setTitle("keychain");
		setFocusedChild(passwordField);
	}

	public final boolean receiveEvent(Event e)
	{
		switch (e.type) {
			case PASSWORD_ENTRY: {
				if (passwordField.toString().length() < 7) {
					new AlertWindow("Password is too short").show();
				} else {
					boolean		allowed = false;
					String		encrypted = null;

					MD5			md5 = new MD5();
					Cipher		cipher = Cipher.getInstance("blowfish");

					md5.update(passwordField.toString().getBytes());
					app.pass = md5.digest();
					md5 = null;

					if (cipher != null && app.pass != null) {
						int			bs;
						int			s;
						int			i;
						byte[]		data;
						byte[]		test = PASSWORD_TEST_DATA.getBytes();

						cipher.init(app.pass, null);

						/*
							The size of data sent into and out of encrypt/decrypt must be a multiple of
							the block size.  Ugg thats annoying.
						*/
						bs = cipher.getBlockSize();

						s = test.length + (bs - (test.length % bs));
						data = new byte[s];

						for (i = 0; i < test.length; i++) {
							data[i] = test[i];
						}

						encrypted = new String(cipher.encrypt(data));
					}

					if (encrypted != null) {
						if (repeatField != null) {
							if (passwordField.toString().equals(repeatField.toString())) {
								/* Store the new encrypted data */
								passwordData.addRecord(encrypted.getBytes());

								/* Cleanup the dialog */
								removeChild(repeatText);
								removeChild(repeatField);

								repeatText = null;
								repeatField = null;

								allowed = true;
							} else {
								new AlertWindow("Passwords do not match").show();
							}
						} else if (passwordData.getRecordCount() > 0) {
							String		stored = new String(passwordData.getRecordData(0));

							/* Compare to the stored encrypted data */
							if (stored.equals(encrypted)) {
								allowed = true;
							} else {
								new AlertWindow("Wrong Password").show();
							}
						}
					} else {
						new AlertWindow("Unable to read stored data").show();
					}

					if (allowed) {
						if (passwordField != null) {
							passwordField.clear();
						}

						if (repeatField != null) {
							passwordField.clear();
						}

						hide();

						app.keys = new KeyList(app);
						app.keys.show();
					}
				}

				break;
			}

			case PASSWORD_CANCEL: {
				setFocusedChild(passwordField);
				app.returnToLauncher();

				break;
			}

			default: {
				return(false);
			}
		}

		return(true);
	}

	public boolean eventWidgetUp(int inWhichWidget, Event inEvent)
	{
		switch (inWhichWidget) {
			case Event.DEVICE_BUTTON_BACK: {
				app.returnToLauncher();

				break;
			}

			default: {
				return(super.eventWidgetUp(inWhichWidget, inEvent));
			}
		}

		return(true);
	}
}

class PassField extends PasswordTextField
{
	LoginWindow			login;

	public PassField(LoginWindow login) {
		super();

		this.login = login;
	}

	public boolean eventKeyUp(char c, Event inEvent)
	{
		switch (c) {
			case '\r': {
				/* They hit enter.  Let em through */

				if (login.passwordData.getRecordCount() > 0) {
					login.receiveEvent(new Event(login, login.PASSWORD_ENTRY));

					return(true);
				}

				/* Fall through */
			}

			default: {
				return(super.eventKeyUp(c, inEvent));
			}
		}
	}
}
