package fr.ippon.tatami.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

import fr.ippon.tatami.domain.User;
import fr.ippon.tatami.service.user.UserService;
import fr.ippon.tatami.web.view.ViewConstants;

/**
 * @author Duy Hai DOAN
 */
@Controller
public class FragmentController
{

	UserService userService;

	@ModelAttribute(value = "currentUser")
	public User getCurrentUser()
	{
		return userService.getCurrentUser();
	}

	@RequestMapping(ViewConstants.URL_FRAGMENT_USER)
	public String homeFragment(Model model)
	{
		return ViewConstants.FRAGMENT_USER;
	}

	@RequestMapping(ViewConstants.URL_FRAGMENT_PROFILE)
	public String profileFragment()
	{
		return ViewConstants.FRAGMENT_PROFILE;
	}

	public void setUserService(UserService userService)
	{
		this.userService = userService;
	}
}
