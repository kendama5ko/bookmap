package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.DefaultTableModel;

import com.mysql.cj.x.protobuf.MysqlxPrepare.Prepare;

public class BookShelfDAO {
    private static final String URL = "jdbc:mysql://localhost:3306/bookmap";
    private static final String USER = "devuser01";
    private static final String PASS = "devuser01";
    Connection con = null;
    PreparedStatement ps = null;
    ResultSet rs;
    private DefaultTableModel tableModel = new DefaultTableModel();
    private int userId;
    private List<Integer> bookIds = new ArrayList<>();

    public void connect() {
        try {
            con = DriverManager.getConnection(URL, USER, PASS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            if (ps != null)
                ps.close();
            if (con != null)
                con.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String[]> createManageBooksList(int userId) {
        // tableactionListenerに渡すためにuserIdを受け取る
        this.userId = userId;
        List<String[]> booksData = new ArrayList<>();
        // データベースからデータを取得
        String selectSQL = "SELECT DISTINCT b.book_id, b.title, a.author_name, g.genre_name, b.total_pages " +
                "FROM user_books ub " +
                "JOIN books b ON ub.book_id = b.book_id " +
                "LEFT JOIN authors a ON b.author_id = a.author_id " +
                "LEFT JOIN genres g ON b.genre_id = g.genre_id " +
                "WHERE ub.user_id = ?";
        connect();
        try (PreparedStatement ps = con.prepareStatement(selectSQL)) {
            ps.setInt(1, userId);
            rs = ps.executeQuery();
            while (rs.next()) {
                int bookId = rs.getInt("book_id");
                this.bookIds.add(bookId); // tableactionListenerで使用するのでbook_idを取得
                String booksTitle = rs.getString("title");
                String authorName = rs.getString("author_name");
                String genreName = rs.getString("genre_name");
                String totalPages = String.valueOf(rs.getInt("total_pages"));
                booksData.add(new String[] { booksTitle, authorName, genreName, totalPages, String.valueOf(bookId) });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return booksData;
    }

    public void deleteBook(int userId, int bookId) {
        String childTableSql = "DELETE FROM user_books WHERE user_id = ? AND book_id = ?";
        String parentTableSql = "DELETE FROM books WHERE book_id = ?";
        connect();
        try {
            con.setAutoCommit(false);
            try (PreparedStatement ps1 = con.prepareStatement(childTableSql);
                    PreparedStatement ps2 = con.prepareStatement(parentTableSql)) {
                ps1.setInt(1, userId);
                ps1.setInt(2, bookId);

                ps2.setInt(1, bookId);

                ps1.addBatch();
                ps2.addBatch();
                ps1.executeBatch();
                ps2.executeBatch();
                con.commit();
            } catch (Exception e) {
                con.rollback();
                e.printStackTrace();
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String updateBookData(int originalRow, int column, String editedData) {
        String columnName = this.tableModel.getColumnName(column);
        String updateSQL = makeSQLStatement(columnName);
        int bookId = this.bookIds.get(originalRow);
        connect();
        try (PreparedStatement ps = con.prepareStatement(updateSQL)) {
            int totalPages;
            if (editedData.matches("\\d+") && columnName.equals("ページ数")) {
                // 整数の場合の処理
                totalPages = Integer.parseInt(editedData);
                ps.setInt(1, totalPages);
            } else if (editedData.matches("[0-9０-９]+") && columnName.equals("ページ数")) {
                totalPages = convertToHalfWidthNumber(editedData);
                ps.setInt(1, totalPages);
            } else {
                // 文字列の場合の処理
                ps.setObject(1, editedData);
            }
            ps.setInt(2, bookId);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            return "申し訳ございません。更新されませんでした。";
        }
        return updatedMessage(columnName);
    }

    private String makeSQLStatement(String columnName) {
        switch (columnName) {
            case "タイトル":
                return "UPDATE books SET title = ? WHERE book_id = ?";
            case "著者":
                return "UPDATE authors SET author_name = ? WHERE author_id = (select author_id from books where book_id = ?)";
            case "ジャンル":
                return "UPDATE genres SET genre_name = ? WHERE genre_id = (select genre_id from books where book_id = ?)";
            case "ページ数":
                return "UPDATE books SET total_pages = ? WHERE book_id = ?";
            default:
                return columnName;
        }
    }

    private String updatedMessage(String columnName) {
        switch (columnName) {
            case "タイトル":
                return "タイトルが変更されました。";
            case "著者":
                return "著者名が変更されました";
            case "ジャンル":
                return "ジャンル名が変更されました";
            case "ページ数":
                return "ページ数が変更されました";
            default:
                return columnName;
        }
    }

    public int convertToHalfWidthNumber(String editedData) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < editedData.length(); i++) {
            char c = editedData.charAt(i);
            if (0xFF10 <= c && c <= 0xFF19) {
                // 全角数字の場合、0xFEE0を引いて半角数字に変換
                sb.append((char) (c - 0xFEE0));
                // 半角数字の場合、そのまま追加
            } else if ('0' <= c && c <= '9') {
                sb.append(c);
            }
        }
        return Integer.parseInt(sb.toString());
    }

}