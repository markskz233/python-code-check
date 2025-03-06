    package com.pythonchecker.service;

    import com.pythonchecker.converter.ErrorRecordToMistakeConverter;
    import com.pythonchecker.model.ErrorRecord;
    import com.pythonchecker.model.Mistake;
    import com.pythonchecker.model.User;
    import com.pythonchecker.repository.ErrorRecordRepository;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.stereotype.Service;

    import java.util.List;
    import java.util.stream.Collectors;

    @Service
    public class ErrorRecordService {

        @Autowired
        private ErrorRecordRepository errorRecordRepository;

        @Autowired
        private ErrorRecordToMistakeConverter converter;

        @Autowired
        private MistakeService mistakeService;

        public ErrorRecord saveErrorRecord(ErrorRecord errorRecord, User user) {
            ErrorRecord savedRecord = errorRecordRepository.save(errorRecord);
            Mistake mistake = converter.convert(savedRecord, user);
            mistakeService.saveMistake(mistake);
            return savedRecord;
        }

        public List<Mistake> getErrorRecordsAsMistakes(User user) {
            List<ErrorRecord> errorRecords = errorRecordRepository.findByUserId(user.getId());
            return errorRecords.stream()
                    .map(record -> converter.convert(record, user))
                    .collect(Collectors.toList());
        }
        public boolean deleteErrorRecord(Long id, User user) {
            ErrorRecord errorRecord = errorRecordRepository.findById(id).orElse(null);
            if (errorRecord == null || !errorRecord.getUserId().equals(user.getId())) {
                return false;
            }
            errorRecordRepository.deleteById(id);
            mistakeService.deleteMistakeByErrorRecordId(id);
            return true;
        }
    }