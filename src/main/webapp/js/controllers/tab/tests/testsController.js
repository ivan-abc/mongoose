/**
 * Created on 18.04.16.
 */
define([
	'jquery',
	'../../../common/util/handlebarsUtil',
	'../../../common/util/templatesUtil',
	'../../../common/util/cssUtil',
	'../../../common/util/tabsUtil',
	'text!../../../../templates/tab/tests/navbar.hbs',
	'text!../../../../templates/tab/tests/base.hbs',
	'./tab/listController',
	'./tab/logsController',
	'./tab/chartsController'
], function ($,
             hbUtil,
             templatesUtil,
             cssUtil,
             tabsUtil,
             navbarTemplate,
             baseTemplate,
             listController,
             logsController,
             chartsController) {

	const TAB_TYPE = templatesUtil.tabTypes();
	const TESTS_TAB_TYPE = templatesUtil.testsTabTypes();
	const plainId = templatesUtil.composeId;
	const jqId = templatesUtil.composeJqId;

	var currentTabType = TESTS_TAB_TYPE.LIST;

	function render() {
		const renderer = rendererFactory();
		renderer.navbar();
		renderer.base();
		listController.render();
		logsController.render();
		chartsController.render();
		makeTabActive(currentTabType);
	}

	const rendererFactory = function () {
		const binder = clickEventBinderFactory();
		const testsBlockElemId = jqId([TAB_TYPE.TESTS, 'block']);

		function renderNavbar() {
			hbUtil.compileAndInsertInsideBefore(testsBlockElemId, navbarTemplate,
				{tabs: TESTS_TAB_TYPE});
			binder.tab();
		}

		function renderBase() {
			hbUtil.compileAndInsertInside(testsBlockElemId, baseTemplate);
		}

		return {
			navbar: renderNavbar,
			base: renderBase
		}
	};

	const clickEventBinderFactory = function () {

		function bindTabClickEvents() {
			tabsUtil.bindTabClickEvents(TESTS_TAB_TYPE, tabJqId, makeTabActive);
		}

		return {
			tab: bindTabClickEvents
		}
	};

	function tabJqId(tabType) {
		return jqId([tabType, TAB_TYPE.TESTS, 'tab']);
	}

	function makeTabActive(tabType) {
		tabsUtil.showTabAsActive(plainId([TAB_TYPE.TESTS, 'tab']), tabType);
		tabsUtil.showActiveTabDependentElements(plainId([TAB_TYPE.TESTS, 'tab', 'dependent']), tabType);
		const testId = listController.currentTestId();
		switch (tabType) {
			case TESTS_TAB_TYPE.LIST:
				break;
			case TESTS_TAB_TYPE.LOGS:
				break;
			case TESTS_TAB_TYPE.CHARTS:
				break;
		}
		currentTabType = tabType;
	}

	function updateTestsList(testsObj) {
		listController.updateTestsList(testsObj);
	}


	return {
		render: render,
		updateTestsList: updateTestsList
	}
});