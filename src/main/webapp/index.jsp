<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri='http://java.sun.com/jsp/jstl/core' prefix='c'%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"%>
<%@ taglib uri="WEB-INF/property.tld" prefix="rt" %>
<!DOCTYPE html>
<html>
	<head>
		<meta charset="utf-8">
		<title>Mongoose-Run</title>
		<link href="css/bootstrap.min.css" rel="stylesheet">
		<link href="css/styles.css" rel="stylesheet">
		<link href="css/bootstrap.vertical-tabs.min.css" rel="stylesheet">
	</head>
	<body>
		<nav class="navbar navbar-default" role="navigation">
			<div class="container-fluid">
				<div class="navbar-header">
					<button type="button" class="navbar-toggle collapsed" data-toggle="collapse"
						data-target="#main-navbar">
						<span class="sr-only">Toggle navigation</span>
						<span class="icon-bar"></span>
						<span class="icon-bar"></span>
						<span class="icon-bar"></span>
					</button>
					<a id="logo" href="/"><img src="images/logo.jpg"></a>
					<a class="navbar-brand" href="/">Mongoose</a>
				</div>
				<div class="collapse navbar-collapse"
					id="main-navbar">
					<ul class="nav navbar-nav">
						<li class="active"><a href="/">Run<span class="sr-only">(current)</span></a></li>
						<li><a href="charts.html">Charts</a></li>
					</ul>

					<p class="navbar-text navbar-right">v.${runTimeConfig.runVersion}</p>
				</div>
			</div>
		</nav>

		<div class="content-wrapper">
			<div class="tabs-wrapper">
				<ul class="nav nav-tabs" role="presentation">
					<li><a href="#chart" data-toggle="tab">Chart</a></li>
					<li class="active"><a href="#configuration" data-toggle="tab">Configuration</a></li>
					<c:forEach var="mode" items="${sessionScope.runmodes}">
						<c:set var="correctMode" value="${fn:replace(mode, '.', '_')}"/>
						<li>
							<a href="#tableTab-${correctMode}" data-toggle="tab">
									${mode}
								<span class="glyphicon glyphicon-remove" value="${mode}"></span>
							</a>
						</li>
					</c:forEach>
				</ul>
			</div>

			<div class="tab-content">
				<div id="chart" class="tab-pane">

				</div>
				<div id="configuration" class="tab-pane active">
					<div class="container-fluid">
						<div class="row">
							<div id="menu" class="col-xs-12 col-sm-4 col-md-3 no-padding">
								<div id="run-modes">
									<select>
										<option value="standalone">standalone</option>
										<option value="client">controller</option>
										<option value="server">driver</option>
										<option value="wsmock">wsmock</option>
									</select>
									<button type="button" id="start" class="btn btn-default">
										Start
									</button>
								</div>

								<div id="config">
									<label for="config-type">Config type</label>
									<select id="config-type">
										<option value="base">base</option>
										<option value="extended">extended</option>
									</select>
									<br/>
									<button class="btn btn-default" id="save-config" type="button">Save</button>
									<a href="/save" id="save-file" class="btn btn-default">Save in file</a>
									<br/>
									<div id="file-visibility">
										<input id="file-checkbox" type="checkbox">
										<label for="file-checkbox">Read config from file</label>
									</div>
									<input id="config-file" type="file" accept=".txt">
								</div>

								<ul class="folders">

								</ul>
							</div>

							<div id="main-content" class="col-xs-12 col-sm-8 col-md-9 no-padding">
								<div id="base">
									<form class="form-horizontal" role="form">
										<div class="standalone client">
											<fieldset>
												<legend>Auth</legend>
												<div class="form-group">
													<label for="backup-auth.id" class="col-sm-3 control-label">auth.id</label>
													<div class="col-sm-9">
														<input type="text" id="backup-auth.id" class="form-control"
												            data-pointer="auth.id" value="${runTimeConfig.authId}"
												            placeholder="Enter 'auth.id' property">
													</div>
												</div>
												<div class="form-group">
													<label for="backup-auth.secret" class="col-sm-3 control-label">auth.secret</label>
													<div class="col-sm-9">
														<input type="text" id="backup-auth.secret" class="form-control"
									                        data-pointer="auth.secret" value="${runTimeConfig.authSecret}"
												            placeholder="Enter 'auth.secret' property">
													</div>
												</div>
											</fieldset>
										</div>

										<div class="standalone client wsmock">
											<fieldset>
												<legend>Storage</legend>
												<div class="standalone client">
													<div class="form-group">
														<label for="backup-storage.addrs" class="col-sm-3 control-label">storage.addrs(data nodes)</label>
														<div class="col-sm-9">
															<input type="text" id="backup-storage.addrs" class="form-control"
																data-pointer="storage.addrs"
																value="${rt:getString(runTimeConfig, 'storage.addrs')}"
																placeholder="Enter 'storage.addrs' property">
														</div>
													</div>
												</div>
												<div class="standalone client wsmock">
													<div class="form-group">
														<label for="backup-storage.api" class="col-sm-3 control-label">storage.api</label>
														<div class="col-sm-9">
															<select id="backup-storage.api" class="form-select" data-pointer="storage.api">
																<option value="backup-${runTimeConfig.storageApi}">${runTimeConfig.storageApi}</option>
																<option value="backup-swift">swift</option>
																<option value="backup-s3">s3</option>
																<option value="backup-atmos">atmos</option>
															</select>
															<br/>
															<button type="button" id="api-button" class="btn btn-primary"
													            data-toggle="modal" data-target="#backup-${runTimeConfig.storageApi}">
																More...
															</button>

															<div class="modal fade" id="backup-s3" tabindex="-1" role="dialog"
													            aria-labelledby="s3Label"
												                aria-hidden="true">
																<div class="modal-dialog">
																	<div class="modal-content">
																		<div class="modal-header">
																			<button type="button" class="close" data-dismiss="modal">
																				<span aria-hidden="true">&times;</span>
																				<span class="sr-only">Close</span>
																			</button>
																			<h4 class="modal-title" id="s3Label">S3</h4>
																		</div>

																		<div class="modal-body">
																			<div class="form-group">
																				<label for="backup-api.s3.bucket" class="col-sm-4 control-label">
																					api.s3.bucket
																				</label>
																				<div class="col-sm-8">
																					<input type="text" id="backup-api.s3.bucket" class="form-control"
																			            data-pointer="api.s3.bucket"
																		                value="${rt:getString(runTimeConfig, 'api.s3.bucket')}"
																			            placeholder="Enter 'api.s3.bucket' property">
																				</div>
																			</div>
																		</div>

																		<div class="modal-footer">
																			<button type="button" class="btn btn-default" data-dismiss="modal">Ok</button>
																		</div>
																	</div>
																</div>
															</div>

															<div class="modal fade" id="backup-swift" tabindex="-1" role="dialog"
													            aria-labelledby="swiftLabel"
											                    aria-hidden="true">
																<div class="modal-dialog">
																	<div class="modal-content">
																		<div class="modal-header">
																			<button type="button" class="close" data-dismiss="modal">
																				<span aria-hidden="true">&times;</span>
																				<span class="sr-only">Close</span>
																			</button>
																			<h4 class="modal-title" id="swiftLabel">Swift</h4>
																		</div>

																		<div class="modal-body">

																		</div>

																		<div class="modal-footer">
																			<button type="button" class="btn btn-default" data-dismiss="modal">Ok</button>
																		</div>
																	</div>
																</div>
															</div>

															<div class="modal fade" id="backup-atmos" tabindex="-1" role="dialog"
													            aria-labelledby="atmosLabel"
													            aria-hidden="true">
																<div class="modal-dialog">
																	<div class="modal-content">
																		<div class="modal-header">
																			<button type="button" class="close" data-dismiss="modal">
																				<span aria-hidden="true">&times;</span>
																				<span class="sr-only">Close</span>
																			</button>
																			<h4 class="modal-title" id="atmosLabel">Atmos</h4>
																		</div>

																		<div class="modal-body">

																		</div>

																		<div class="modal-footer">
																			<button type="button" class="btn btn-default" data-dismiss="modal">Ok</button>
																		</div>
																	</div>
																</div>
															</div>
														</div>
													</div>
												</div>
											</fieldset>
										</div>

										<div class="client">
											<fieldset>
												<legend>Controller</legend>
												<div class="form-group">
													<label for="backup-remote.servers" class="col-sm-3 control-label">remote.servers(drivers)</label>
													<div class="col-sm-9">
														<input type="text" id="backup-remote.servers" class="form-control"
												            data-pointer="remote.servers"
												            value="${rt:getString(runTimeConfig, 'remote.servers')}"
												            placeholder="Enter 'remote.servers' property">
													</div>
												</div>
											</fieldset>
										</div>

										<div class="standalone client">
											<fieldset>
												<legend>Data</legend>
												<div class="form-group">
													<label for="backup-data" class="col-sm-3 control-label">data</label>
													<div class="col-sm-9">
														<select id="backup-data" class="form-select">
															<option>time</option>
															<option>objects</option>
														</select>
													</div>
												</div>

												<div id="objects" class="form-group">
													<label for="backup-data.count" class="col-sm-3 control-label">data.count</label>
													<div class="col-sm-9">
														<input type="text" id="backup-data.count" class="form-control"
									                        data-pointer="data.count"
												            value="${runTimeConfig.dataCount}"
												            placeholder="Enter 'data.count' property">
													</div>
												</div>

												<div id="time" class="form-group complex">
													<c:set var="runTimeArray" value="${fn:split(runTimeConfig.runTime, '.')}"/>
													<label for="backup-run.time.input" class="col-sm-3 control-label">run.time</label>
													<div class="col-sm-9">
														<input type="text" id="backup-run.time.input" class="form-control pre-select"
												            value="${runTimeArray[0]}">
														<select class="form-select" id="backup-run.time.select">
															<option>${runTimeArray[1]}</option>
															<option>days</option>
															<option>hours</option>
															<option>minutes</option>
															<option>seconds</option>
														</select>
													</div>
													<input type="hidden" id="backup-run.time" class="form-control"
											            data-pointer="run.time"
											            value="${runTimeArray[0]}.${runTimeArray[1]}">
												</div>

												<div class="form-group">
													<label for="backup-data.size.min" class="col-sm-3 control-label">data.size.min</label>
													<div class="col-sm-9">
														<input type="text" id="backup-data.size.min" class="form-control"
											                data-pointer="data.size.min"
												            value="${rt:getString(runTimeConfig, 'data.size.min')}"
												            placeholder="Enter 'data.size.min' property">
													</div>
												</div>

												<div class="form-group">
													<label for="backup-data.size.max" class="col-sm-3 control-label">data.size.max</label>
													<div class="col-sm-9">
														<input type="text" id="backup-data.size.max" class="form-control"
												            data-pointer="data.size.max"
												            value="${rt:getString(runTimeConfig, 'data.size.max')}"
												            placeholder="Enter 'data.size.max' property">
													</div>
												</div>

												<div class="form-group">
													<label for="backup-data.src.fpath" class="col-sm-3 control-label">data.src.fpath</label>
													<div class="col-sm-9">
														<input type="text" id="backup-data.src.fpath" class="form-control"
												            data-pointer="data.src.fpath"
												            value="${rt:getString(runTimeConfig, 'data.src.fpath')}"
									                        placeholder="Enter relative path to the list of objects on remote host. Format: log/<run.mode>/<run.id>/<filename>">
													</div>
												</div>
											</fieldset>
										</div>

										<div class="standalone client wsmock server">
											<fieldset>
												<legend>Run</legend>
												<div class="form-group">
													<label for="backup-run.id" class="col-sm-3 control-label">run.id</label>
													<div class="col-sm-9">
														<input type="text" id="backup-run.id" class="form-control"
												            data-pointer="run.id"
											                placeholder="Enter 'run.id' property. For example, ${runTimeConfig.runId}">
													</div>
												</div>
											</fieldset>

											<div class="standalone client">
												<div class="form-group">
													<label for="backup-run.scenario.name" class="col-sm-3 control-label">run.scenario.name</label>
													<div class="col-sm-9">
														<select id="backup-run.scenario.name" class="form-select" data-pointer="run.scenario.name">
															<option value="backup-${runTimeConfig.runScenarioName}">
																${runTimeConfig.runScenarioName}
															</option>
															<option value="backup-single">single</option>
															<option value="backup-chain">chain</option>
															<option value="backup-rampup">rampup</option>
														</select>
														<br/>
														<button type="button" id="scenario-button" class="btn btn-primary"
											                data-toggle="modal"
												            data-target="#backup-${runTimeConfig.runScenarioName}">
															More...
														</button>

														<div class="modal fade" id="backup-single" tabindex="-1" role="dialog" aria-labelledby="singleLabel"
											                aria-hidden="true">
															<div class="modal-dialog">
																<div class="modal-content">
																	<div class="modal-header">
																		<button type="button" class="close" data-dismiss="modal">
																			<span aria-hidden="true">&times;</span>
																			<span class="sr-only">Close</span>
																		</button>
																		<h4 class="modal-title" id="singleLabel">Single</h4>
																	</div>

																	<div class="modal-body">
																		<div class="form-group">
																			<label for="backup-scenario.single.load"
																			       class="col-sm-6 control-label">scenario.single.load</label>
																			<div class="col-sm-6">
																				<select id="backup-scenario.single.load" class="form-select"
																		            data-pointer="scenario.single.load">
																					<option value="backup-${rt:getString(runTimeConfig,
																						'scenario.single.load')}">
																							${rt:getString(runTimeConfig, 'scenario.single.load')}
																					</option>
																					<option value="backup-create">create</option>
																					<option value="backup-read">read</option>
																					<option value="backup-update">update</option>
																					<option value="backup-delete">delete</option>
																					<option value="backup-append">append</option>
																				</select>
																			</div>
																		</div>

																		<hr/>

																		<div id="backup-create">
																			<fieldset>
																				<legend>Create</legend>
																				<div class="form-group">
																					<label class="col-sm-6 control-label" for="backup-load.create.threads">
																						load.create.threads
																					</label>
																					<div class="col-sm-6">
																						<input type="text" id="backup-load.create.threads"
																				            class="form-control"
																			                data-pointer="load.create.threads"
																			                value="${rt:getString(runTimeConfig, 'load.create.threads')}"
																				            placeholder="Enter 'load.create.threads' property">
																					</div>
																				</div>
																			</fieldset>
																		</div>

																		<div id="backup-read">
																			<fieldset>
																				<legend>Read</legend>
																				<div class="form-group">
																					<label class="col-sm-6 control-label" for="backup-load.read.threads">
																						load.read.threads
																					</label>
																					<div class="col-sm-6">
																						<input type="text" id="backup-load.read.threads"
																				            class="form-control"
																				            data-pointer="load.read.threads"
																					        value="${rt:getString(runTimeConfig, 'load.read.threads')}"
																					        placeholder="Enter 'load.read.threads' property">
																					</div>
																				</div>

																				<div class="form-group">
																					<label for="backup-load.read.verify.content"
																					       class="col-sm-6 control-label">load.read.verify.content</label>
																					<div class="col-sm-6">
																						<select id="backup-load.read.verify.content" class="form-select"
																				            data-pointer="load.read.verify.content">
																							<option>
																								${rt:getString(runTimeConfig, 'load.read.verify.content')}
																							</option>
																							<option>true</option>
																							<option>false</option>
																						</select>
																					</div>
																				</div>
																			</fieldset>
																		</div>

																		<div id="backup-update">
																			<fieldset>
																				<legend>Update</legend>
																				<div class="form-group">
																					<label class="col-sm-6 control-label" for="backup-load.update.threads">
																						load.update.threads
																					</label>
																					<div class="col-sm-6">
																						<input type="text" id="backup-load.update.threads"
																				            class="form-control"
																				            value="${rt:getString(runTimeConfig, 'load.update.threads')}"
																				            data-pointer="load.update.threads"
																				            placeholder="Enter 'load.update.threads' property">
																					</div>
																				</div>

																				<div class="form-group">
																					<label class="col-sm-6 control-label" for="backup-load.update.per.item">
																						load.update.per.item
																					</label>
																					<div class="col-sm-6">
																						<input type="text" id="backup-load.update.per.item"
																				            class="form-control"
																				            value="${rt:getString(runTimeConfig, 'load.update.per.item')}"
																				            data-pointer="load.update.per.item"
																				            placeholder="Enter 'load.update.per.item' property">
																					</div>
																				</div>
																			</fieldset>
																		</div>

																		<div id="backup-delete">
																			<fieldset>
																				<legend>Delete</legend>
																				<div class="form-group">
																					<label class="col-sm-6 control-label" for="backup-load.delete.threads">
																						load.delete.threads
																					</label>
																					<div class="col-sm-6">
																						<input type="text" id="backup-load.delete.threads"
																				            class="form-control"
																			                value="${rt:getString(runTimeConfig, 'load.delete.threads')}"
																				            data-pointer="load.delete.threads"
																				            placeholder="Enter 'load.delete.threads' property">
																					</div>
																				</div>
																			</fieldset>
																		</div>

																		<div id="backup-append">
																			<fieldset>
																				<legend>Append</legend>
																				<div class="form-group">
																					<label class="col-sm-6 control-label" for="backup-load.append.threads">
																						load.append.threads
																					</label>
																					<div class="col-sm-6">
																						<input type="text" id="backup-load.append.threads"
																				            class="form-control"
																				            value="${rt:getString(runTimeConfig, 'load.append.threads')}"
																				            data-pointer="load.append.threads"
																				            placeholder="Enter 'load.append.threads' property">
																					</div>
																				</div>
																			</fieldset>
																		</div>
																	</div>

																	<div class="modal-footer">
																		<button type="button" class="btn btn-default" data-dismiss="modal">Ok</button>
																	</div>
																</div>
															</div>
														</div>

														<div class="modal fade" id="backup-chain" tabindex="-1" role="dialog" aria-labelledby="chainLabel"
												            aria-hidden="true">
															<div class="modal-dialog">
																<div class="modal-content">
																	<div class="modal-header">
																		<button type="button" class="close" data-dismiss="modal">
																			<span aria-hidden="true">&times;</span>
																			<span class="sr-only">Close</span>
																		</button>
																		<h4 class="modal-title" id="chainLabel">Chain</h4>
																	</div>

																	<div class="modal-body">
																		<div class="form-group">
																			<label for="backup-scenario.chain.load" class="col-sm-6 control-label">
																				scenario.chain.load
																			</label>
																			<div class="col-sm-6">
																				<input type="text" class="form-control" id="backup-scenario.chain.load"
																		            value="${rt:getString(runTimeConfig, 'scenario.chain.load')}"
																		            data-pointer="scenario.chain.load"
																		            placeholder="Enter 'scenario.chain.load' property">
																			</div>
																		</div>

																		<div role="tabpanel">
																			<ul class="nav nav-tabs" role="tablist">
																				<li role="presentation" class="active">
																					<a href="#backuptab-create" aria-controls="backuptab-create"
																				        role="tab" data-toggle="tab">
																						Create
																					</a>
																				</li>
																				<li role="presentation">
																					<a href="#backuptab-read" aria-controls="backuptab-read"
																				        role="tab" data-toggle="tab">
																						Read
																					</a>
																				</li>
																				<li role="presentation">
																					<a href="#backuptab-update" aria-controls="backuptab-update"
																				        role="tab" data-toggle="tab">
																						Update
																					</a>
																				</li>
																				<li role="presentation">
																					<a href="#backuptab-delete" aria-controls="backuptab-delete"
																				        role="tab" data-toggle="tab">
																						Delete
																					</a>
																				</li>
																				<li role="presentation">
																					<a href="#backuptab-append" aria-controls="backuptab-append"
																				        role="tab" data-toggle="tab">
																						Append
																					</a>
																				</li>
																			</ul>

																			<div class="tab-content modal-tabs">
																				<div role="tabpanel" class="tab-pane active" id="backuptab-create">
																					<div class="form-group">
																						<label class="col-sm-6 control-label"
																				            for="backuptab-load.create.threads">
																							load.create.threads
																						</label>
																						<div class="col-sm-6">
																							<input type="text" id="backuptab-load.create.threads"
																					            class="form-control"
																					            value="${rt:getString(runTimeConfig, 'load.create.threads')}"
																					            data-pointer="load.create.threads"
																					            placeholder="Enter 'load.create.threads' property">
																						</div>
																					</div>
																				</div>
																				<div role="tabpanel" class="tab-pane" id="backuptab-read">
																					<div class="form-group">
																						<label class="col-sm-6 control-label" for="backuptab-load.read.threads">
																							load.read.threads
																						</label>
																						<div class="col-sm-6">
																							<input type="text" id="backuptab-load.read.threads"
																					            class="form-control"
																					            value="${rt:getString(runTimeConfig, 'load.read.threads')}"
																					            data-pointer="load.read.threads"
																					            placeholder="Enter 'load.read.threads' property">
																						</div>
																					</div>

																					<div class="form-group">
																						<label for="backuptab-load.read.verify.content"
																				            class="col-sm-6 control-label">
																							load.read.verify.content
																						</label>
																						<div class="col-sm-6">
																							<select id="backuptab-load.read.verify.content" class="form-select"
																					            data-pointer="load.read.verify.content">
																								<option>
																									${rt:getString(runTimeConfig, 'load.read.verify.content')}
																								</option>
																								<option>true</option>
																								<option>false</option>
																							</select>
																						</div>
																					</div>
																				</div>
																				<div role="tabpanel" class="tab-pane" id="backuptab-update">
																					<div class="form-group">
																						<label class="col-sm-6 control-label"
																				            for="backuptab-load.update.threads">
																							load.update.threads
																						</label>
																						<div class="col-sm-6">
																							<input type="text" id="backuptab-load.update.threads"
																					            class="form-control"
																					            value="${rt:getString(runTimeConfig, 'load.update.threads')}"
																					            data-pointer="load.update.threads"
																					            placeholder="Enter 'load.update.threads' property">
																						</div>
																					</div>

																					<div class="form-group">
																						<label class="col-sm-6 control-label"
																				            for="backuptab-load.update.per.item">
																							load.update.per.item
																						</label>
																						<div class="col-sm-6">
																							<input type="text" id="backuptab-load.update.per.item"
																					            class="form-control"
																					            value="${rt:getString(runTimeConfig, 'load.update.per.item')}"
																					            data-pointer="load.update.per.item"
																					            placeholder="Enter 'load.update.per.item' property">
																						</div>
																					</div>
																				</div>
																				<div role="tabpanel" class="tab-pane" id="backuptab-delete">
																					<div class="form-group">
																						<label class="col-sm-6 control-label"
																				            for="backuptab-load.delete.threads">
																							load.delete.threads
																						</label>
																						<div class="col-sm-6">
																							<input type="text" class="form-control" id="backuptab-load.delete.threads"
																					            value="${rt:getString(runTimeConfig, 'load.delete.threads')}"
																					            data-pointer="load.delete.threads"
																					            placeholder="Enter 'load.delete.threads' property">
																						</div>
																					</div>
																				</div>
																				<div role="tabpanel" class="tab-pane" id="backuptab-append">
																					<div class="form-group">
																						<label class="col-sm-6 control-label"
																				            for="backuptab-load.append.threads">
																							load.append.threads
																						</label>
																						<div class="col-sm-6">
																							<input type="text" id="backuptab-load.append.threads"
																					            class="form-control"
																					            value="${rt:getString(runTimeConfig, 'load.append.threads')}"
																					            data-pointer="load.append.threads"
																					            placeholder="Enter 'load.append.threads' property">
																						</div>
																					</div>
																				</div>
																			</div>
																		</div>

																		<hr/>

																		<div class="form-group">
																			<label for="backup-scenario.chain.simultaneous"
																	            class="col-sm-6 control-label">
																				scenario.chain.simultaneous
																			</label>
																			<div class="col-sm-6">
																				<select id="backup-scenario.chain.simultaneous" class="form-select"
																		            data-pointer="scenario.chain.simultaneous">
																					<option>
																						${rt:getString(runTimeConfig, 'scenario.chain.simultaneous')}
																					</option>
																					<option>true</option>
																					<option>false</option>
																				</select>
																			</div>
																		</div>
																	</div>

																	<div class="modal-footer">
																		<button type="button" class="btn btn-default" data-dismiss="modal">Ok</button>
																	</div>
																</div>
															</div>
														</div>

														<div class="modal fade" id="backup-rampup" tabindex="-1"
											                role="dialog" aria-labelledby="rampupLabel"
												            aria-hidden="true">
															<div class="modal-dialog">
																<div class="modal-content">
																	<div class="modal-header">
																		<button type="button" class="close" data-dismiss="modal">
																			<span aria-hidden="true">&times;</span>
																			<span class="sr-only">Close</span>
																		</button>
																		<h4 class="modal-title" id="rampupLabel">Rampup</h4>
																	</div>

																	<div class="modal-body">

																		<div class="form-group">
																			<label class="col-sm-6 control-label" for="chain-load">
																				You need to configure chain.load
																			</label>
																			<div class="col-sm-6">
																				<button type="button" class="btn btn-default" id="chain-load">
																					Configure
																				</button>
																			</div>
																		</div>
																		<div class="form-group">
																			<label for="backup-scenario.rampup.thread.counts"
																	            class="col-sm-4 control-label">
																				thread.counts
																			</label>
																			<div class="col-sm-8">
																				<input type="text" id="backup-scenario.rampup.thread.counts"
																	                class="form-control"
																		            value="${rt:getString(runTimeConfig, 'scenario.rampup.thread.counts')}"
																		            data-pointer="scenario.rampup.thread.counts"
																		            placeholder="Enter 'scenario.rampup.thread.counts' property">
																			</div>
																		</div>

																		<div class="form-group">
																			<label for="backup-scenario.rampup.sizes" class="col-sm-4 control-label">
																				load.rampup.sizes
																			</label>
																			<div class="col-sm-8">
																				<input type="text" id="backup-scenario.rampup.sizes" class="form-control"
																		            value="${rt:getString(runTimeConfig, 'scenario.rampup.sizes')}"
																		            data-pointer="scenario.rampup.sizes"
																		            placeholder="Enter 'scenario.rampup.sizes' property">
																			</div>
																		</div>
																	</div>

																	<div class="modal-footer">
																		<button type="button" class="btn btn-default" data-dismiss="modal">Ok</button>
																	</div>
																</div>
															</div>
														</div>
													</div>
												</div>

												<div class="form-group">
													<label for="backup-run.request.retries" class="col-sm-3 control-label">
														run.request.retries
													</label>
													<div class="col-sm-9">
														<select id="backup-run.request.retries" class="form-select"
												            data-pointer="run.request.retries">
															<option>
																${rt:getString(runTimeConfig, "run.request.retries")}
															</option>
															<option>true</option>
															<option>false</option>
														</select>
													</div>
												</div>
											</div>
										</div>
									</form>
								</div>
								<div id="extended">
									<form class="form-horizontal" id="main-form" role="form">
										<input type="hidden" name="run.mode" id="run-mode" value="standalone">

										<!-- Input fields with labels from JS -->
										<div id="configuration-content">

										</div>
									</form>
								</div>
							</div>
						</div>
					</div>
				</div>
				<c:forEach var="mode" items="${sessionScope.runmodes}">
					<c:set var="correctMode" value="${fn:replace(mode, '.', '_')}"/>
					<div class="tab-pane table-pane" id="tableTab-${correctMode}" tab-id="${mode}">
						<div class="left-side">
							<div class="menu-wrapper">
								<div class="col-xs-8">
									<ul class="nav nav-tabs tabs-left">
										<li class="active"><a href="#${correctMode}messages-csv" data-toggle="tab">messages.csv</a></li>
										<li><a href="#${correctMode}errors-log" data-toggle="tab">errors.log</a></li>
										<li><a href="#${correctMode}perf-avg-csv" data-toggle="tab">perf.avg.csv</a></li>
										<li><a href="#${correctMode}perf-sum-csv" data-toggle="tab">perf.sum.csv</a></li>
									</ul>
								</div>
							</div>
						</div>
						<div class="right-side">
							<c:if test="${empty sessionScope.stopped[mode]}">
								<button type="button" class="default stop"><span>Stop</span></button>
							</c:if>
							<div class="log-wrapper">
								<div class="tab-content">
									<div class="tab-pane active" id="${correctMode}messages-csv">
										<table class="table">
											<thead>
											<tr>
												<th>Level</th>
												<th>LoggerName</th>
												<th>ThreadName</th>
												<th>TimeMillis</th>
												<th>Message</th>
											</tr>
											</thead>
											<tbody>
											</tbody>
										</table>
										<button type="button" class="default clear">Clear</button>
									</div>
									<div class="tab-pane" id="${correctMode}errors-log">
										<table class="table">
											<thead>
											<tr>
												<th>Level</th>
												<th>LoggerName</th>
												<th>ThreadName</th>
												<th>TimeMillis</th>
												<th>Message</th>
											</tr>
											</thead>
											<tbody>
											</tbody>
										</table>
										<button type="button" class="default clear">Clear</button>
									</div>
									<div class="tab-pane" id="${correctMode}perf-avg-csv">
										<table class="table">
											<thead>
											<tr>
												<th>Level</th>
												<th>LoggerName</th>
												<th>ThreadName</th>
												<th>TimeMillis</th>
												<th>Message</th>
											</tr>
											</thead>
											<tbody>
											</tbody>
										</table>
										<button type="button" class="default clear">Clear</button>
									</div>
									<div class="tab-pane" id="${correctMode}perf-sum-csv">
										<table class="table">
											<thead>
											<tr>
												<th>Level</th>
												<th>LoggerName</th>
												<th>ThreadName</th>
												<th>TimeMillis</th>
												<th>Message</th>
											</tr>
											</thead>
											<tbody>
											</tbody>
										</table>
										<button type="button" class="default clear">Clear</button>
									</div>
								</div>
							</div>
						</div>
					</div>
				</c:forEach>
			</div>
		</div>
		<script type="text/javascript" src="js/d3.min.js"></script>
		<script type="text/javascript" src="js/jquery-2.1.0.min.js"></script>
		<script type="text/javascript" src="js/script.js"></script>
		<script type="text/javascript" src="js/bootstrap.min.js"></script>
		<script>
			propertiesMap = ${runTimeConfig.propertiesMap};
		</script>
	</body>
</html>