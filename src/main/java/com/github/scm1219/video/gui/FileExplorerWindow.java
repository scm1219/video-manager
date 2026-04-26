package com.github.scm1219.video.gui;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.TrayIcon.MessageType;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneLayout;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.TableRowSorter;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.apache.commons.lang3.StringUtils;

import com.github.scm1219.video.AppConfig;
import com.github.scm1219.video.domain.Disk;
import com.github.scm1219.video.domain.DiskManager;
import com.github.scm1219.video.gui.table.FileTable;
import com.github.scm1219.video.gui.table.FileTableModel;
import com.github.scm1219.video.gui.tree.FileTree;
import com.github.scm1219.video.gui.tree.FileTreeCellRenderer;
import com.github.scm1219.video.gui.tree.FileTreeNode;
import com.github.scm1219.utils.FileUtils;

import java.awt.Font;

public class FileExplorerWindow extends JFrame
		implements MenuBarBuilder.ThemeMenuCallback, MenuBarBuilder.IndexValidationCallback, ContextMenuBuilder.NavigationCallback {
	private static final long serialVersionUID = 1L;
	private JPanel topPanel;
    private JPanel leftPanel;
    private JPanel rightPanel;
    private JPanel indexInfoPanel;
    private JSplitPane bottomSplitPane;
    private JSplitPane leftSplitPane;
    private JTextField directoryField;
    private JTextField searchField;
    private JCheckBox realtimeSearchCheckBox;  // 实时搜索复选框
    private Timer searchTimer;            // 延迟定时器
    private FileTreeNode fileTreeRoot;
    private FileTree fileTree;
    private FileTable fileTable;
    private Stack<File> navigationStack;
    private JButton btnBack;
    private JButton btnSearch;
    private JButton btnCleanSearch;
    private JButton btnSearchDir;
    private JButton btnRefresh;
    private JCheckBox videoOnlyCheckBox;
    private JCheckBox showAllDisksCheckBox;
    private JScrollPane spTable;
    private JScrollPane spIndexInfo;
    private FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private JPopupMenu menu;
    private JMenuItem mNavigateTo;
    private JMenuItem mScanDirectory;
    private JMenuItem mRenameToSimple;
    private javax.swing.JLabel lblStatusBar;  // 状态栏标签
    private JMenu themeMenu;
    private boolean videoOnly = false;
    private boolean showAllDisks = false;
    private boolean isSearchMode = false;

	// ---- MenuBarBuilder.ThemeMenuCallback 实现 ----

	@Override
	public void switchTheme(String themeName) {
		// 检查是否与当前主题相同，相同则不执行切换
		String currentTheme = ThemeManager.getInstance().getCurrentTheme();
		if (currentTheme.equalsIgnoreCase(themeName)) {
			return;
		}

		boolean success = ThemeManager.getInstance().applyTheme(themeName);
		if (success) {
			// 刷新所有组件的 UI
			ThemeManager.updateUI();
			updateIndexInfo();
			// 更新主题菜单选中状态
			MenuBarBuilder.updateThemeMenuSelection(themeMenu, getCurrentTheme());
			// 显示切换成功提示
			JOptionPane.showMessageDialog(this,
				"已切换到" + ThemeManager.getThemeDisplayName(themeName),
				"主题切换",
				JOptionPane.INFORMATION_MESSAGE);
		} else {
			// 显示切换失败提示
			JOptionPane.showMessageDialog(this,
				"主题切换失败，请查看控制台日志",
				"错误",
				JOptionPane.ERROR_MESSAGE);
		}
	}

	@Override
	public String getCurrentTheme() {
		return ThemeManager.getInstance().getCurrentTheme();
	}

	// ---- MenuBarBuilder.IndexValidationCallback 实现 ----

	@Override
	public void validateAndCleanupIndex() {
		MenuBarBuilder.showIndexValidationDialog(this, fileSystemView);
	}

	// ---- ContextMenuBuilder.NavigationCallback 实现 ----

	@Override
	public Stack<File> getNavigationStack() {
		return navigationStack;
	}

	@Override
	public File getCurrentDir() {
		return currentDir;
	}

    private void initData(){
        navigationStack = new Stack<File>();
        File[] rootDisks;

        if (showAllDisks) {
            rootDisks = File.listRoots();
        } else {
            List<Disk> listDisk = DiskManager.getInstance().listDisk();
            rootDisks = new File[listDisk.size()];
            for (int i = 0; i < rootDisks.length; i++) {
                rootDisks[i]=listDisk.get(i).getRoot();
            }
        }

        if(rootDisks.length>0) {
	        fileTreeRoot = new FileTreeNode(rootDisks[0], true);

	        File[] files = rootDisks;
	        for (int i = 0; i < files.length; i++) {
	            if (files[i].isDirectory())
	            {
	                FileTreeNode childNode = new FileTreeNode(files[i], false);
	                Disk disk = DiskManager.getInstance().findDisk(files[i]);
	                childNode.setIndexed(disk != null && disk.needIndex());
	                fileTreeRoot.add(childNode);
	            }
	        }
        }
    }

    private void createComponent(){
        topPanel = new JPanel();
        leftPanel = new JPanel();
        rightPanel = new JPanel();
        indexInfoPanel = new JPanel();
        bottomSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        leftSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        directoryField = new JTextField();
        directoryField.setEditable(false);
        searchField =  new JTextField();
        fileTree = new FileTree();
        fileTable = new FileTable();
        btnBack = new JButton("后退");
        btnSearch =  new JButton("搜索文件");
        btnCleanSearch =  new JButton("清空搜索");
        btnSearchDir = new JButton("搜索文件夹");
        btnRefresh = new JButton("刷新磁盘");
        videoOnlyCheckBox = new JCheckBox("只显示视频文件");
        showAllDisksCheckBox = new JCheckBox("显示所有磁盘");
        realtimeSearchCheckBox = new JCheckBox("实时搜索");
        realtimeSearchCheckBox.setSelected(false);  // 默认不选中
        lblStatusBar = new javax.swing.JLabel(" ");  // 初始化状态栏

        // 初始化实时搜索定时器（700ms延迟）
        searchTimer = new Timer(AppConfig.SEARCH_DEBOUNCE_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (realtimeSearchCheckBox.isSelected() && StringUtils.isNotBlank(searchField.getText())) {
                    performSearch();  // 执行搜索
                }
            }
        });
        searchTimer.setRepeats(false);  // 仅触发一次，不重复

        // 使用 ContextMenuBuilder 创建右键菜单
        ContextMenuBuilder.MenuItems contextMenuItems = ContextMenuBuilder.buildContextMenu(this, this);
        menu = contextMenuItems.popupMenu;
        mNavigateTo = contextMenuItems.navigateToItem;
        mScanDirectory = contextMenuItems.scanDirectoryItem;
        mRenameToSimple = contextMenuItems.renameToSimpleItem;

        // 使用 MenuBarBuilder 创建菜单栏
        JMenuBar menuBar = MenuBarBuilder.buildMenuBar(this, this, this, fileSystemView);
        themeMenu = (JMenu) menuBar.getMenu(0);  // 第一个菜单是主题菜单
        this.setJMenuBar(menuBar);
    }

    public static final Comparator<File> FILE_COMPARATOR= new Comparator<File>() {
        @Override
        public int compare(File f1, File f2) {
            if (f1.isDirectory()){
                if (f2.isDirectory()){
                    return f1.getName().compareToIgnoreCase(f2.getName());
                } else {
                    return -1;
                }
            } else {
                if (!f2.isDirectory()){
                    return f1.getName().compareToIgnoreCase(f2.getName());
                } else {
                    return 1;
                }
            }
        }
    };

    public static final Comparator<Long> FILE_SIZE_COMPARATOR = new Comparator<Long>() {
        @Override
        public int compare(Long size1, Long size2) {
            return Long.compare(size1, size2);
        }
    };

    /**
     * 执行文件搜索（供按钮、键盘、实时搜索调用）
     */
    private void performSearch() {
        String keyword = searchField.getText();
        if (StringUtils.isNotBlank(keyword)) {
            List<File> files = DiskManager.getInstance().searchAllFiles(keyword);
            updateSearchResult(files);
        }
    }

    /**
     * 更新搜索结果
     * @param files
     */
    private void updateSearchResult(List<File> files) {
    	isSearchMode = true;
    	// 统计已删除的文件数量
    	int deletedCount = 0;
    	for (File file : files) {
    		if (!file.exists()) {
    			deletedCount++;
    		}
    	}
    	File[] fileArray = new File[files.size()];
    	files.toArray(fileArray);
    	setFileTable(fileArray);
    	// 搜索后滚动条回到顶部
    	spTable.getViewport().setViewPosition(new Point(0, 0));
    	// 更新状态栏显示搜索结果数量和已删除数量
    	updateStatusBar(files.size(), true, deletedCount);
    }

    /**
     * 设置表格里的数据
     * @param files 文件数组
     */
    private void setFileTable(File[] files) {
        setFileTable(files, false);
    }

    /**
     * 设置表格里的数据
     * @param files 文件数组
     * @param showParentRow 是否显示"返回上一级"虚拟行
     */
    private void setFileTable(File[] files, boolean showParentRow) {
    	List<File> fileList = new java.util.ArrayList<File>();
        for (int i = 0; i < files.length; i++){
            if (videoOnly) {
                if (files[i].isDirectory() || FileUtils.isVideoFile(files[i])) {
                    fileList.add(files[i]);
                }
            } else {
                fileList.add(files[i]);
            }
        }
        Collections.sort(fileList, FILE_COMPARATOR);
        Object[][] fileData = new Object[fileList.size()][5];
        for (int i = 0; i < fileData.length; i++){
            for (int j = 0; j < 5; j++){
                fileData[i][j] = fileList.get(i);
            }
        }

        FileTableModel model = new FileTableModel(fileData, showParentRow);
        fileTable.setModel(model);
        TableRowSorter<FileTableModel> sort = new TableRowSorter<>(model);
        sort.setComparator(0, FILE_COMPARATOR);
        sort.setComparator(3, FILE_SIZE_COMPARATOR);
        fileTable.setRowSorter(sort);
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(50);
        fileTable.getColumnModel().getColumn(4).setPreferredWidth(150);
        spTable.getViewport().setBackground(Color.white);

        // 更新状态栏显示文件总数
        updateStatusBar(fileList.size(), false, 0);
    }

    /**
     * 更新状态栏显示
     * @param count 文件数量
     * @param isSearch 是否为搜索模式
     * @param deletedCount 已删除文件数量（仅搜索模式有效）
     */
    private void updateStatusBar(int count, boolean isSearch, int deletedCount) {
        if (isSearch) {
            if (deletedCount > 0) {
                lblStatusBar.setText("搜索结果: " + count + " 个项目，已删除: " + deletedCount + " 个");
            } else {
                lblStatusBar.setText("搜索结果: " + count + " 个项目");
            }
        } else {
            lblStatusBar.setText("共 " + count + " 个项目");
        }
    }

    private File currentDir = null;

    private void updateIndexInfo() {
    	StringBuilder info = new StringBuilder();
    	info.append("<html><body style='padding: 10px; font-family: sans-serif;'>");
    	info.append("<h3 style='margin-top: 0; color: "+ThemeManager.getPrimaryColor()+";'>索引信息</h3>");

    	List<Disk> disks = DiskManager.getInstance().listDisk();
    	info.append("<p><strong>索引磁盘数量:</strong> ").append(disks.size()).append("</p>");

    	if (!disks.isEmpty()) {
    		info.append("<p><strong>磁盘列表:</strong></p>");
    		info.append("<ul>");
    		for (Disk disk : disks) {
    			String displayName = fileSystemView.getSystemDisplayName(disk.getRoot());
    			if (displayName == null || displayName.isEmpty()) {
    				displayName = disk.getPath();
    			}
    			info.append("<li>").append(displayName).append(" (").append(disk.getPath()).append(")</li>");
    		}
    		info.append("</ul>");
    	}

    	info.append("<p style='color: "+ThemeManager.getSecondaryTextColor()+"; '>提示: 双击视频文件可直接播放</p>");
    	info.append("</body></html>");

    	indexInfoPanel.removeAll();
    	javax.swing.JLabel lblInfo = new javax.swing.JLabel(info.toString());
    	lblInfo.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
    	indexInfoPanel.setLayout(new java.awt.BorderLayout());
    	indexInfoPanel.add(lblInfo, java.awt.BorderLayout.NORTH);
    	indexInfoPanel.revalidate();
    	indexInfoPanel.repaint();
    }

    private void refreshTree(boolean showPrompt) {
    	// 保存当前选中的文件
    	TreePath selectedPath = fileTree.getSelectionPath();
    	File selectedFile = null;
    	if (selectedPath != null) {
    		FileTreeNode node = (FileTreeNode) selectedPath.getLastPathComponent();
    		selectedFile = node.getFile();
    	}

    	// 执行刷新逻辑
    	DiskManager.getInstance().reloadDisks();
    	initData();

    	DefaultTreeModel dfTreeModel = new DefaultTreeModel(fileTreeRoot) {
			private static final long serialVersionUID = 1L;

			@Override
            public boolean isLeaf(Object node) {
                FileTreeNode treeNode = (FileTreeNode) node;
                if (treeNode.isDummyRoot()){
                    return false;
                }
                return ((File)treeNode.getUserObject()).isFile();
            }
        };
        fileTree.setModel(dfTreeModel);
        fileTree.setCellRenderer(new FileTreeCellRenderer());
        fileTree.setRootVisible(false);

        // 恢复选择或选择第一个
        if (selectedFile != null) {
        	restoreSelection(selectedFile);
        } else if (fileTree.getRowCount() > 0) {
        	fileTree.setSelectionRow(0);
        }

        updateIndexInfo();

        if (showPrompt) {
        	List<Disk> disks = DiskManager.getInstance().listDisk();
        	if(disks.size()<1) {
        		JOptionPane.showMessageDialog(null, "未发现需要索引的分区\n请在需要索引的分区根目录下\n添加  "+Disk.FLAG_FILE+" 文件", "提示", MessageType.INFO.ordinal());
        	} else {
        		JOptionPane.showMessageDialog(null, "刷新成功，共发现 "+disks.size()+" 个需要索引的磁盘", "提示", MessageType.INFO.ordinal());
        	}
        }
    }

    /**
     * 恢复树的选择到指定的文件
     * @param targetFile 目标文件对象
     */
    private void restoreSelection(File targetFile) {
    	TreeNode root = (TreeNode) fileTree.getModel().getRoot();

    	// 遍历根节点查找匹配的磁盘
    	for (int i = 0; i < root.getChildCount(); i++) {
    		FileTreeNode child = (FileTreeNode) root.getChildAt(i);
    		if (child.getFile().equals(targetFile)) {
    			// 找到匹配，设置选中路径
    			TreePath path = new TreePath(new Object[]{root, child});
    			fileTree.setSelectionPath(path);
    			fileTree.scrollPathToVisible(path);
    			return;
    		}
    	}

    	// 未找到匹配，选择第一个
    	if (fileTree.getRowCount() > 0) {
    		fileTree.setSelectionRow(0);
    	}
    }

    /**
     * 判断指定文件是否为磁盘根目录
     * @param file 文件对象
     * @return 如果是磁盘根目录返回 true
     */
    private boolean isDiskRoot(File file) {
        if (file == null || !file.isDirectory()) {
            return false;
        }

        // 检查是否为 File.listRoots() 中的根目录
        File[] roots = File.listRoots();
        for (File root : roots) {
            if (file.equals(root)) {
                return true;
            }
        }

        // 检查是否为索引磁盘列表中的根目录
        List<Disk> disks = DiskManager.getInstance().listDisk();
        for (Disk disk : disks) {
            if (file.equals(disk.getRoot())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 验证文件是否可以跳转到其父目录
     * @param file 要验证的文件
     * @return true 可以跳转，false 不可以
     */
    @Override
    public boolean canNavigateToFile(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        File parentDir = file.getParentFile();
        return parentDir != null && parentDir.exists();
    }

    @Override
    public void updateTable(File file, Boolean isBack){
        isSearchMode = false;
        String path = file.getAbsolutePath();
        if (path == null)
            return;
        directoryField.setText(path);
        currentDir = file;
        if (!isBack){
            navigationStack.push(file);
        }

        final File targetFile = file;

        SwingWorker<File[], Void> worker = new SwingWorker<File[], Void>() {
            @Override
            protected File[] doInBackground() {
                return fileSystemView.getFiles(targetFile, false);
            }

            @Override
            protected void done() {
                try {
                    File[] files = get();
                    // 判断是否在磁盘根目录
                    boolean isRootDirectory = isDiskRoot(targetFile);
                    setFileTable(files, !isRootDirectory);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    /**
     * 更新文件表格并重建完整的目录路径层级（用于目录树点击）
     * @param file 目标目录
     */
    private void updateTableWithPath(File file) {
        isSearchMode = false;
        String path = file.getAbsolutePath();
        if (path == null)
            return;
        directoryField.setText(path);
        currentDir = file;

        // 重建完整的目录层级路径（从根目录到目标目录）
        File current = file;
        java.util.Stack<File> tempStack = new java.util.Stack<>();

        // 从目标目录向上遍历到根目录
        while (current != null && current.exists()) {
            tempStack.push(current);
            current = current.getParentFile();
        }

        // 反向压入栈（从根到目标目录）
        navigationStack.clear();
        while (!tempStack.isEmpty()) {
            navigationStack.push(tempStack.pop());
        }

        final File targetFile = file;

        SwingWorker<File[], Void> worker = new SwingWorker<File[], Void>() {
            @Override
            protected File[] doInBackground() {
                return fileSystemView.getFiles(targetFile, false);
            }

            @Override
            protected void done() {
                try {
                    File[] files = get();
                    // 判断是否在磁盘根目录
                    boolean isRootDirectory = isDiskRoot(targetFile);
                    setFileTable(files, !isRootDirectory);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void addComponentListeners(){
        fileTree.addTreeSelectionListener(new TreeSelectionListener(){
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                javax.swing.JTree tree = (javax.swing.JTree)e.getSource();
                FileTreeNode selectNode = (FileTreeNode)tree.getLastSelectedPathComponent();
                if (selectNode != null){
                    File file = selectNode.getFile();
                    updateTableWithPath(file);
                }
            }
        });

        fileTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1)
                {
                    javax.swing.JTree tree = (javax.swing.JTree)e.getSource();
                    FileTreeNode selectNode = (FileTreeNode)tree.getLastSelectedPathComponent();
                    if (selectNode != null){
                        File file = selectNode.getFile();
                        updateTableWithPath(file);
                    }
                }
            }
        });

        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                FileTable fileTable = (FileTable)e.getSource();
                int row = fileTable.rowAtPoint(e.getPoint());

                // 检查是否点击虚拟行
                FileTableModel model = fileTable.getFileTableModel();
                if (model != null && model.isParentRow(row)) {
                    // 双击虚拟行触发返回上级
                    if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                        if (navigationStack.size() > 1) {
                            navigationStack.pop();
                            updateTable(navigationStack.peek(), true);
                        }
                    }
                    return;
                }

                File file = (File) fileTable.getValueAt(row, 0);
                if (e.getClickCount() == 1){
                } else if (e.getClickCount() == 2){
                	if(FileUtils.isVideoFile(file)) {
                		String filePath = file.getAbsolutePath();
                		if (ClickDebouncer.shouldOpen(filePath)) {
                			try {
                				FileUtils.openVideoFile(file);
                			} catch (Exception ex) {
                				ClickDebouncer.recordError(filePath);
                			}
                		}
                	}else if(file.isDirectory()){
                		updateTable(file, false);
                	}
                }
                if(e.getButton() == MouseEvent.BUTTON3) {
                	fileTable.setRowSelectionInterval(row, row);

                	// 动态控制"转到"菜单项状态
                	mNavigateTo.setEnabled(isSearchMode && canNavigateToFile(file));

                	// 仅对文件夹显示"扫描此目录"菜单项
                	mScanDirectory.setVisible(file.isDirectory());

                	// 仅对文件夹显示"文件夹名转简体"菜单项
                	mRenameToSimple.setVisible(file.isDirectory());

                	menu.show(fileTable, e.getX(), e.getY());
                }
            }
        });

        btnBack.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (navigationStack.size()>1) {
                    navigationStack.pop();
                    updateTable(navigationStack.peek(), true);
                }
            }
        });
        btnCleanSearch.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				searchField.setText("");
				if(currentDir!=null) {
					updateTable(currentDir,false);
				}
			}
		});
        btnSearch.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				performSearch();
			}
		});

        searchField.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ENTER) {
					performSearch();
				}
			}
		});

        // 添加搜索框文本变化监听（实时搜索）
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                restartSearchTimer();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                restartSearchTimer();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // JTextField 使用 PlainDocument，不会触发此事件
                // 保留方法以满足 DocumentListener 接口要求
                restartSearchTimer();
            }

            /**
             * 重新启动搜索定时器（防抖处理）
             */
            private void restartSearchTimer() {
                if (realtimeSearchCheckBox.isSelected()) {
                    searchTimer.restart();
                }
            }
        });

        btnSearchDir.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				String test = searchField.getText();
				if(StringUtils.isNotBlank(test)) {
					List<File> files = DiskManager.getInstance().searchAllDirs(test);
					updateSearchResult(files);
				}
			}
		});

        videoOnlyCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                videoOnly = videoOnlyCheckBox.isSelected();
                if (currentDir != null) {
                    updateTable(currentDir, true);
                }
            }
        });

        showAllDisksCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                boolean newShowAllDisks = showAllDisksCheckBox.isSelected();
                // 仅当状态真正改变时才刷新
                if (newShowAllDisks != showAllDisks) {
                    showAllDisks = newShowAllDisks;
                    refreshTree(false);
                }
            }
        });

        btnRefresh.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshTree(true);
            }
        });
    }

    private void initComponent(){
        initData();
        createComponent();
        addComponentListeners();

        spTable = new JScrollPane(fileTable);
        spTable.setBackground(Color.white);
        // 设置table 列宽
        DefaultTreeModel dfTreeModel = new DefaultTreeModel(fileTreeRoot) {
			private static final long serialVersionUID = 1L;

			@Override
            public boolean isLeaf(Object node) {
                FileTreeNode treeNode = (FileTreeNode) node;
                if (treeNode.isDummyRoot()){
                    return false;
                }
                return ((File)treeNode.getUserObject()).isFile();
            }
        };
        fileTree.setModel(dfTreeModel);
        fileTree.setCellRenderer(new FileTreeCellRenderer());
        fileTree.setRootVisible(false);
        fileTree.setSelectionRow(0);

        JScrollPane scroll = new JScrollPane(fileTree);
        scroll.setLayout(new ScrollPaneLayout());
        scroll.getViewport().setBackground(Color.WHITE);

        spIndexInfo = new JScrollPane(indexInfoPanel);
        spIndexInfo.setBackground(Color.white);
        spIndexInfo.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        spIndexInfo.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        spIndexInfo.setPreferredSize(new Dimension(200, 150));
        spIndexInfo.setMinimumSize(new Dimension(200, 100));

        leftSplitPane.setTopComponent(scroll);
        leftSplitPane.setBottomComponent(spIndexInfo);
        leftSplitPane.setResizeWeight(0.8);

        JPanel leftPanelTop = new JPanel();
        leftPanelTop.setLayout(new GridBagLayout());
        leftPanelTop.add(btnRefresh, new GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.WEST,
                GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));
        leftPanelTop.add(showAllDisksCheckBox, new GridBagConstraints(1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
                GridBagConstraints.HORIZONTAL, new Insets(5, 5, 5, 5), 0, 0));

        leftPanel.setLayout(new GridBagLayout());
        leftPanel.add(leftPanelTop, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0, GridBagConstraints.EAST,
                GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
        leftPanel.add(leftSplitPane, new GridBagConstraints(0, 1, 1, 1, 1.0, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

        rightPanel.setLayout(new GridBagLayout());
        rightPanel.add(spTable,new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        rightPanel.add(lblStatusBar, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.WEST,
                GridBagConstraints.HORIZONTAL, new Insets(2, 5, 2, 5), 0, 0));

        //搜索
        topPanel.setLayout(new GridBagLayout());
        topPanel.add(btnSearch, new GridBagConstraints(0, 0, 1, 1, 0.05, 1.0, GridBagConstraints.WEST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        topPanel.add(btnSearchDir,new GridBagConstraints(1, 0, 1, 1, 0.05, 1.0, GridBagConstraints.WEST,
                GridBagConstraints.BOTH, new Insets(1, 0, 0, 0), 0, 0));
        topPanel.add(searchField,new GridBagConstraints(2, 0, 1, 1, 0.82, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        topPanel.add(realtimeSearchCheckBox, new GridBagConstraints(3, 0, 1, 1, 0.13, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 5, 0, 0), 0, 0));
        topPanel.add(videoOnlyCheckBox, new GridBagConstraints(3, 1, 1, 1, 0.13, 0.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(2, 5, 0, 0), 0, 0));


        //路径
        topPanel.add(btnBack, new GridBagConstraints(0, 1, 1, 1, 0.05, 1.0, GridBagConstraints.WEST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));
        topPanel.add(btnCleanSearch, new GridBagConstraints(1, 1, 1, 1, 0.05, 1.0, GridBagConstraints.WEST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

        topPanel.add(directoryField,new GridBagConstraints(2, 1, 1, 1, 0.95, 1.0, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0));

        bottomSplitPane.setLeftComponent(leftPanel);
        bottomSplitPane.setRightComponent(rightPanel);
        leftPanel.setMinimumSize(new Dimension(200,height));

        Container container = this.getContentPane();
        container.setLayout(new GridBagLayout());
        container.add(topPanel, new GridBagConstraints(0, 0, 1, 1, 1.0, 0.05, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(2, 0, 0, 0), 0, 0));
        container.add(bottomSplitPane, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.95, GridBagConstraints.EAST,
                GridBagConstraints.BOTH, new Insets(4, 2, 2, 2), 0, 0));

        updateIndexInfo();

        // 设置搜索框快捷键
        setupSearchShortcuts();

        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        this.setBounds(screenSize.width/2 - width/2, screenSize.height/2 - height/2, width, height);
        this.setMinimumSize(new Dimension(width, height));
    }

    /**
     * 设置搜索框的快捷键
     * - Ctrl+F: 定位搜索框并选中全部文本
     * - Alt+W: 清空搜索内容并刷新表格（回到当前目录）
     */
    private void setupSearchShortcuts() {
        // 获取输入映射和动作映射
        InputMap inputMap = searchField.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = searchField.getActionMap();

        // 注册 Ctrl+F: 定位搜索框并选中全部文本
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK), "focusSearch");
        actionMap.put("focusSearch", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.requestFocusInWindow();
                searchField.selectAll();
            }
        });

        // 注册 Alt+W: 清空搜索内容并刷新表格（与"清空搜索"按钮逻辑一致）
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, KeyEvent.ALT_DOWN_MASK), "clearSearch");
        actionMap.put("clearSearch", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                searchField.setText("");
                if(currentDir!=null) {
                    updateTable(currentDir,false);
                }
                searchField.requestFocusInWindow();  // 保持焦点在搜索框
            }
        });
    }

    /**
     * 激活搜索框焦点（供外部调用）
     * 用于窗口初始化后将焦点定位到搜索框
     */
    public void focusSearchField() {
        searchField.requestFocusInWindow();
    }

    private int width = AppConfig.WINDOW_WIDTH;
    private int height = AppConfig.WINDOW_HEIGHT;
    public FileExplorerWindow(){
        super("视频文件浏览器");
        initComponent();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                super.windowClosed(e);
                // 释放定时器资源
                if (searchTimer != null && searchTimer.isRunning()) {
                    searchTimer.stop();
                }
            }
        });
        if(DiskManager.getInstance().listDisk().size()<1) {
        	JOptionPane.showMessageDialog(null, "未发现需要索引的分区\n请在需要索引的分区根目录下\n添加  "+Disk.FLAG_FILE+" 文件", "警告", MessageType.INFO.ordinal());
        }
    }

}
