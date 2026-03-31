package com.psms.util;

import com.psms.repository.ApplicationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApplicationCodeGenerator {

    // Định dạng ngày theo ISO basic: YYYYMMDD (ví dụ: 20260331)
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    // Pattern dùng để validate và parse mã hồ sơ từ DB
    // Group 1: phần ngày (8 chữ số), Group 2: phần sequence (5 chữ số)
    private static final Pattern CODE_PATTERN = Pattern.compile("^HS-(\\d{8})-(\\d{5})$");

    // Giới hạn 99,999 = 5 chữ số tối đa, đủ cho mọi tình huống thực tế của PSMS
    private static final int MAX_DAILY_SEQUENCE = 99_999;

    private final ApplicationRepository applicationRepository;

    private final Clock clock;

    private final Object monitor = new Object();

    /** Ngày đang được cache. Khi sang ngày mới, cache bị reset. */
    private LocalDate cachedDate;

    /** Sequence tiếp theo sẽ được dùng. Tăng dần sau mỗi lần sinh mã thành công. */
    private int nextSequence;

    /** Constructor chính dùng cho Spring IoC — inject Clock hệ thống thực. */
    @Autowired
    public ApplicationCodeGenerator(ApplicationRepository applicationRepository) {
        this(applicationRepository, Clock.systemDefaultZone());
    }

    ApplicationCodeGenerator(ApplicationRepository applicationRepository, Clock clock) {
        // Fail-fast: ném NullPointerException ngay tại constructor thay vì để NullPointerException
        // nổ muộn hơn tại runtime (ví dụ: bên trong synchronized block khó debug hơn nhiều).
        this.applicationRepository = Objects.requireNonNull(applicationRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    public String generate() {
        synchronized (monitor) {
            LocalDate today = LocalDate.now(clock);

            // Nếu ngày thay đổi (sang ngày mới) hoặc lần đầu chạy → cần khởi tạo lại từ DB
            if (!today.equals(cachedDate)) {
                initializeSequence(today);
            }

            if (nextSequence > MAX_DAILY_SEQUENCE) {
                throw new IllegalStateException(
                    "Đã vượt quá giới hạn 99,999 mã hồ sơ trong ngày " + today.format(DATE_FORMATTER)
                );
            }

            // Trả về mã hiện tại, sau đó tăng sequence cho lần gọi tiếp theo (post-increment)
            return buildCode(today, nextSequence++);
        }
    }

    private void initializeSequence(LocalDate date) {
        String prefix = buildPrefix(date);

        // Truy vấn DB: tìm mã hồ sơ có prefix ngày hôm nay, lấy mã lớn nhất (DESC LIMIT 1)
        // Dùng Optional.map để xử lý trường hợp chưa có mã nào trong ngày → trả về 0
        int lastSequence = applicationRepository
                .findTopByApplicationCodeStartingWithOrderByApplicationCodeDesc(prefix)
                .map(existing -> extractSequence(existing.getApplicationCode(), prefix))
                .orElse(0); // Chưa có mã nào trong ngày → bắt đầu từ sequence 1

        // Cập nhật cache — sau bước này không cần truy vấn DB nữa cho đến ngày hôm sau
        cachedDate = date;
        nextSequence = lastSequence + 1;
    }

    /**
     * Tạo phần prefix của mã hồ sơ: {@code HS-YYYYMMDD-}
     **/
    private String buildPrefix(LocalDate date) {
        return "HS-" + date.format(DATE_FORMATTER) + "-";
    }

    /**
     * Ghép prefix và sequence thành mã hồ sơ hoàn chỉnh.
     * Sequence được padding thành 5 chữ số (ví dụ: 1 → "00001").
     */
    private String buildCode(LocalDate date, int sequence) {
        return buildPrefix(date) + String.format("%05d", sequence);
    }

    private int extractSequence(String applicationCode, String expectedPrefix) {
        Matcher matcher = CODE_PATTERN.matcher(applicationCode);

        // Nếu mã không khớp pattern hoặc không đúng prefix ngày → dữ liệu DB bị lỗi
        // Ném exception ngay thay vì bỏ qua, để tránh sinh mã trùng một cách im lặng
        if (!matcher.matches() || !applicationCode.startsWith(expectedPrefix)) {
            throw new IllegalStateException("Mã hồ sơ không đúng định dạng: " + applicationCode);
        }

        // Group 2 là phần sequence 5 chữ số, ví dụ "00042" → 42
        return Integer.parseInt(matcher.group(2));
    }
}

